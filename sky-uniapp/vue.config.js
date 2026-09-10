const BACKEND = 'http://localhost:8080'

module.exports = {
  productionSourceMap: false,
  transpileDependencies: ['@dcloudio/uni-ui'],
  devServer: {
    // 8080 被 sky-backend 占用,小程序 H5 走 8081
    port: 8081,
    proxy: {
      // 接口请求同源代理到本地后端,规避跨域
      '/user': { target: BACKEND, changeOrigin: true },
      '/notify': { target: BACKEND, changeOrigin: true },
      '/files': { target: BACKEND, changeOrigin: true }
    }
  }
}