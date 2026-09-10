// Node 23+ 移除了 util.isRegExp / isString / isObject 等已废弃别名，
// 而 @dcloudio/uni-cli-shared 依赖的 postcss-urlrewrite 仍在调用它们。
// 通过 NODE_OPTIONS=--require 在构建前补齐，避免小程序端构建直接崩掉。
const util = require('util')

if (typeof util.isRegExp !== 'function') {
	util.isRegExp = (value) => Object.prototype.toString.call(value) === '[object RegExp]'
}
if (typeof util.isString !== 'function') {
	util.isString = (value) => typeof value === 'string'
}
if (typeof util.isObject !== 'function') {
	util.isObject = (value) => value !== null && typeof value === 'object'
}
