import Cookies from 'js-cookie';

// App
const sidebarStatusKey = 'sidebar_status';
export const setSidebarStatus = (sidebarStatus: string) => Cookies.set(sidebarStatusKey, sidebarStatus);

// User
const storeId = 'storeId';
export const getStoreId = () => Cookies.get(storeId);
export const setStoreId = (id: string) => Cookies.set(storeId, id);

// User
const tokenKey = 'token';
export const getToken = () => Cookies.get(tokenKey);
export const setToken = (token: string) => Cookies.set(tokenKey, token);
export const removeToken = () => Cookies.remove(tokenKey);

// userInfo
const userInfoKey = 'userInfo';
export const getUserInfo = () => Cookies.get(userInfoKey);
export const removeUserInfo = () => Cookies.remove(userInfoKey);
