import { request } from "../../utils/request.js"

// ============ 用户登录 ============

// 用户登录（微信 jsCode 换令牌对）
export const userLogin = (params) =>
	request({
		url: '/user/user/login',
		method: 'POST',
		params
	})

// ============ 菜品 / 分类 ============

// 菜品和套餐的分类
export const getCategoryList = (params) =>
	request({
		url: '/user/category/list',
		method: 'GET',
		params
	})

// 按分类查询菜品列表
export const dishListByCategoryId = (params) =>
	request({
		url: '/user/dish/list',
		method: 'GET',
		params
	})

// 按分类查询套餐列表
export const querySetmeaList = (params) =>
	request({
		url: '/user/setmeal/list',
		method: 'GET',
		params
	})

// 首页套餐详情（套餐内含哪些菜品）
export const querySetmealDishById = (params) =>
	request({
		url: `/user/setmeal/dish/${params.id}`,
		method: 'GET'
	})

// ============ 购物车 ============

// 获取购物车集合
export const getShoppingCartList = (params) =>
	request({
		url: '/user/shoppingCart/list',
		method: 'GET',
		params
	})

// 购物车加菜
export const newAddShoppingCartAdd = (params) =>
	request({
		url: '/user/shoppingCart/add',
		method: 'POST',
		params
	})

// 购物车减菜
export const newShoppingCartSub = (params) =>
	request({
		url: '/user/shoppingCart/sub',
		method: 'POST',
		params
	})

// 清空购物车
export const delShoppingCart = (params) =>
	request({
		url: '/user/shoppingCart/clean',
		method: 'DELETE',
		params
	})

// ============ 店铺 ============

// 店铺营业状态
export const getShopStatus = (params) =>
	request({
		url: '/user/shop/status',
		method: 'GET'
	})

// 店铺信息（本机后端未提供该接口，调用方已做容错）
export const getMerchantInfo = (params) =>
	request({
		url: '/user/shop/getMerchantInfo',
		method: 'GET'
	})

// ============ 收货地址 ============

// 查询地址列表
export const queryAddressBookList = (params) =>
	request({
		url: '/user/addressBook/list',
		method: 'GET',
		params
	})

// 查询默认地址
export const getAddressBookDefault = () =>
	request({
		url: '/user/addressBook/default',
		method: 'GET'
	})

// 新增地址
export const addAddressBook = (params) =>
	request({
		url: '/user/addressBook',
		method: 'POST',
		params
	})

// 修改地址
export const editAddressBook = (params) =>
	request({
		url: '/user/addressBook',
		method: 'PUT',
		params
	})

// 设置默认地址
export const putAddressBookDefault = (params) =>
	request({
		url: '/user/addressBook/default',
		method: 'PUT',
		params
	})

// 按 id 查询地址
export const queryAddressBookById = (params) =>
	request({
		url: `/user/addressBook/${params.id}`,
		method: 'GET',
		params
	})

// 删除地址
export const delAddressBook = (id) =>
	request({
		url: `/user/addressBook?id=${id}`,
		method: 'DELETE'
	})

// ============ 订单 ============

// 提交订单
export const submitOrderSubmit = (params) =>
	request({
		url: '/user/order/submit',
		method: 'POST',
		params
	})

// 订单支付
export const paymentOrder = (params) =>
	request({
		url: '/user/order/payment',
		method: 'PUT',
		params
	})

// 历史订单分页
export const getOrderPage = (params) =>
	request({
		url: '/user/order/historyOrders',
		method: 'GET',
		params
	})

// 订单详情
export const getOrderDetail = (params) =>
	request({
		url: `/user/order/orderDetail/${params}`,
		method: 'GET'
	})

// 取消订单
export const cancelOrder = (params) =>
	request({
		url: `/user/order/cancel/${params}`,
		method: 'PUT'
	})

// 催单
export const reminderOrder = (params) =>
	request({
		url: `/user/order/reminder/${params}`,
		method: 'GET'
	})

// 再来一单
export const repetitionOrder = (params) =>
	request({
		url: `/user/order/repetition/${params}`,
		method: 'POST',
		params
	})
