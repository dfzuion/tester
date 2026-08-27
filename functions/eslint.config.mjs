import js from "@eslint/js";
import tseslint from "typescript-eslint";

export default [
  { ignores: ["lib/**", "lib-test/**"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.ts"],
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "no-console": "off",
      eqeqeq: ["error", "smart"],
    },
  },
];
