import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import vueTsEslintConfig from '@vue/eslint-config-typescript'

export default [
  {
    ignores: ['dist/**', 'node_modules/**']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsEslintConfig(),
  {
    files: ['**/*.{ts,vue,js,mjs,cjs}'],
    rules: {
      // 类型检查交给 vue-tsc,静态规则在这里会误报
      'no-undef': 'off',
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ],
      // 存量代码里 any 较多,单独立项收敛,不在本轮引入噪音
      '@typescript-eslint/no-explicit-any': 'off',
      // 组件名多为单个单词(Empty/Chart/Pagination),不强制
      'vue/multi-word-component-names': 'off',
      // 标签书写风格交给 prettier
      'vue/html-self-closing': 'off',
      'vue/component-name-in-template-casing': 'off',
      // 属性换行由 prettier 按 80 列决定,开这里会和它来回打架
      'vue/first-attribute-linebreak': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/html-indent': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-closing-bracket-newline': 'off'
    }
  },
  prettier
]
