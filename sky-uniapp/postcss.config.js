// uni-app H5 的 rpx 需要在编译期转成 %?N?% 占位符，运行时再由 h5-vue-style-loader 换成 px/rem
const uniPostcss = require('@dcloudio/vue-cli-plugin-uni/packages/postcss')
const autoprefixer = require('autoprefixer')

module.exports = {
  plugins: [uniPostcss({}), autoprefixer]
}
