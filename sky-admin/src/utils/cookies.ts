import Cookies from 'js-cookie';

// App
const sidebarStatusKey = 'sidebar_status';
export const setSidebarStatus = (sidebarStatus: string) => Cookies.set(sidebarStatusKey, sidebarStatus);

// User
const storeId = 'storeId';
export const getStoreId = () => Cookies.get(storeId);
export const setStoreId = (id: string) => Cookies.set(storeId, id);

// Token
const tokenKey = 'token';
export const getToken = () => Cookies.get(tokenKey);
export const setToken = (token: string) => Cookies.set(tokenKey, token);
export const removeToken = () => Cookies.remove(tokenKey);

// 记住的登录账号
const usernameKey = 'username';
export const getUsername = () => Cookies.get(usernameKey);
export const setUsername = (username: string) => Cookies.set(usernameKey, username);
export const removeUsername = () => Cookies.remove(usernameKey);

// 登录用户信息
const userInfoKey = 'user_info';
export const getUserInfo = () => Cookies.get(userInfoKey);
export const setUserInfo = (userInfo: string) => Cookies.set(userInfoKey, userInfo);
export const removeUserInfo = () => Cookies.remove(userInfoKey);
