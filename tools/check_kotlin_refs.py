"""
Flags a capitalised identifier used as a call or a qualifier that is neither
imported, declared in the same file, nor declared anywhere in the app source.

The previous version only looked at identifiers at the start of a statement,
which missed `Arrangement.SpaceBetween` sitting in an argument list - exactly
the one that broke the build.
"""
import re, os, sys, subprocess

ALWAYS_OK = {
    # Kotlin/Java built-ins and things reached through an already-checked root
    "String","Int","Long","Boolean","Double","Float","List","Map","Set","Unit",
    "Math","System","Exception","Throwable","Any","Nothing","Pair","Triple","Array",
    "Regex","Result","Char","Byte","Short","Number","Comparable","Iterable","Sequence",
    "OptIn","Suppress","JvmStatic","Deprecated",
    "BuildConfig",  # generated at build time, never present in source
    # nested objects reached via an imported root (Icons.AutoMirrored.Filled.X)
    "AutoMirrored","Filled","Outlined","Rounded","Sharp","TwoTone","Default",
}

root = "app/src/main/java"
declared = set()
for dirpath, _, files in os.walk(root):
    for f in files:
        if f.endswith(".kt"):
            t = open(os.path.join(dirpath, f)).read()
            # generic functions: fun <T> Foo(...)
            declared |= set(re.findall(
                r'\b(?:class|object|interface|enum class|data class|annotation class)\s+([A-Z]\w*)', t))
            declared |= set(re.findall(r'\bfun\s+(?:<[^>]*>\s*)?([A-Z]\w*)', t))
            # enum entries: ALL("All"), NEWEST("Newest"),
            declared |= set(re.findall(r'^\s*([A-Z][A-Z0-9_]{2,})\s*[(,;]?\s*$', t, re.M))
            declared |= set(re.findall(r'^\s*([A-Z][A-Z0-9_]{2,})\s*[(,]', t, re.M))
            # companion / top-level constants: val ROLES = ...
            declared |= set(re.findall(r'\b(?:const\s+)?va[lr]\s+([A-Z][A-Za-z0-9_]*)', t))
            # single-line enum bodies: enum class X { ACTIVE("Active"), WON("Won") }
            for line in re.findall(r'^.*\benum class\b.*$', t, re.M):
                declared |= set(re.findall(r'\b([A-Z][A-Z0-9_]{1,})\b', line))

def strip(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'"""[\s\S]*?"""', '""', t)
    t = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', t)
    return t

def check(path):
    raw = open(path).read()
    body = strip(raw)
    imports = set(re.findall(r'^import\s+[\w.]*\.(\w+)', raw, re.M))
    star = re.findall(r'^import\s+([\w.]*)\.\*', raw, re.M)
    # anything fully qualified inline is fine
    qualified = set(re.findall(r'\b(?:[a-z]\w*\.)+([A-Z]\w*)', body))
    used = set(re.findall(r"(?<![.\w])([A-Z]\w+)(?=\s*[.(])", body))
    bad = sorted(u for u in used
                 if u not in imports and u not in declared
                 and u not in ALWAYS_OK and u not in qualified and not star)
    return bad

files = [a for a in sys.argv[1:] if a.endswith(".kt") and os.path.isfile(a)]
if not files:
    # default: every Kotlin file in the app
    files = []
    for dirpath, _, fs in os.walk(root):
        files += [os.path.join(dirpath, f) for f in fs if f.endswith(".kt")]

fail = False
for f in files:
    bad = check(f)
    if bad:
        fail = True
        print("UNRESOLVED  %s -> %s" % (f.split("/")[-1], ", ".join(bad)))
print("checked %d files -- %s" % (len(files), "PROBLEMS ABOVE" if fail else "all clear"))
sys.exit(1 if fail else 0)
