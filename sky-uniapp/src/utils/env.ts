// #ifdef H5
// H5 走 vite.config.ts 里的 devServer 代理，同源无需写死域名
export const baseUrl = ''
// #endif
// #ifndef H5
// 小程序/APP 需要写后端直连地址（本机调试用 localhost，真机用电脑局域网 IP）
export const baseUrl = 'http://localhost:8080'
// #endif
