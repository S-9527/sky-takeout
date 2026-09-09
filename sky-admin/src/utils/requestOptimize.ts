import md5 from 'md5';
import type { AxiosRequestConfig } from 'axios';

//根据请求的地址，方式，参数，统一计算出当前请求的md5值作为key
const getRequestKey = (config?: AxiosRequestConfig): string => {
    if (!config) {
        // 如果没有获取到请求的相关配置信息，根据时间戳生成
        return md5(String(+new Date()));
    }

    const target = config.params ?? config.data;
    const source = JSON.stringify(target ?? null);
    return md5(config.url + '&' + config.method + '&' + source);
}

// 存储key值
const pending: Record<string, boolean> = {};
// 检查key值
const checkPending = (key: string): boolean => !!pending[key];
// 删除key值
const removePending = (key: string): void => {
    delete pending[key];
};

export {
    getRequestKey,
    pending,
    checkPending,
    removePending
}