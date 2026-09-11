// H5 走 vite.config.ts 里的 devServer 代理，同源无需写死域名
// 小程序/APP 需要写后端直连地址（本机调试用 localhost，真机用电脑局域网 IP）
let baseUrl: string

// #ifdef H5
baseUrl = ''
// #endif
// #ifndef H5
baseUrl = 'http://localhost:8080'
// #endif

export { baseUrl }