import store from './../store'
import { baseUrl } from './env'

// 参数：url:请求地址 params:请求参数 method:请求方式
export function request({ url = '', params = {}, method = 'GET' }) {
	store.commit('setLodding', false)
	const header = {
		'Accept': 'application/json',
		'Content-Type': 'application/json',
		'Authorization': 'Bearer ' + (store.state.token || '')
	}

	const requestRes = new Promise((resolve, reject) => {
		uni.request({
			url: baseUrl + url,
			data: params,
			header: header,
			method: method,
			success: (res) => {
				const { data } = res
				if (data.code === 200) {
					resolve(data)
				} else {
					reject(data)
				}
			},
			fail: (err) => {
				reject({ code: -1, msg: err.errMsg })
			}
		})
	})
	return requestRes
}
