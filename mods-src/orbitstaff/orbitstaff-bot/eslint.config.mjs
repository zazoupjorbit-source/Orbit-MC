import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**"] },
  ...tseslint.configs.recommended,
  {
    rules: {
      // Le routage Discord lit des types d'union via des acces controles.
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
);
