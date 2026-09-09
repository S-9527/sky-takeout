function dateFormat(fmt: string, time: number) {
  const date = new Date(time);
  let ret: RegExpExecArray | null;
  const opt: Record<string, string> = {
    // 年
    "Y+": date.getFullYear().toString(),
    // 月
    "m+": (date.getMonth() + 1).toString(),
    // 日
    "d+": date.getDate().toString()
    // 有其他格式化字符需求可以继续添加，必须转化成字符串
  };
  for (const k in opt) {
    ret = new RegExp("(" + k + ")").exec(fmt);
    if (ret) {
      fmt = fmt.replace(
        ret[1],
        ret[1].length === 1 ? opt[k] : opt[k].padStart(ret[1].length, "0")
      );
    }
  }
  return fmt;
}

// 今日 0 点(本地时区)。
// 原来是 new Date(new Date().toLocaleDateString())——先转成本地化字符串再解析回来,
// 属于隐式转换:日期字符串是 locale 相关的(可能带时间、可能带前导零),解析结果依赖运行环境
const startOfToday = (): number => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
}

// js获取昨日的日期
export const get1stAndToday = (): string[] => {
  const toData = startOfToday()
  const yesterdayStart = toData - 3600 * 24 * 1000;
  const yesterdayEnd = yesterdayStart + 24 * 60 * 60 * 1000 - 1;
  const startDay1 = dateFormat("YYYY-mm-dd", yesterdayStart);
  const endDay1 = dateFormat("YYYY-mm-dd", yesterdayEnd);
  return [startDay1, endDay1];
};
// 获取昨日、今日日期
export const getday = (): string[] => {
  const toData = startOfToday()
  const yesterdays= toData - 3600 * 24 * 1000;
  const yesterday = dateFormat("YYYY.mm.dd", yesterdays);
  const today = dateFormat("YYYY.mm.dd", toData);
  return [yesterday,today];
};

// 获取近7日
export const past7Day = (): string[] => {
  const toData = startOfToday()
  const past7daysStart = toData - 7 * 3600 * 24 * 1000;
  const past7daysEnd = toData - 1;
  const days7Start = dateFormat("YYYY-mm-dd", past7daysStart);
  const days7End = dateFormat("YYYY-mm-dd", past7daysEnd);
  return [days7Start, days7End];
};

// 获取近30日
export const past30Day = (): string[] => {
  const toData = startOfToday()
  const past30daysStart = toData - 30 * 3600 * 24 * 1000;
  const past30daysEnd = toData - 1;
  const days30Start = dateFormat("YYYY-mm-dd", past30daysStart);
  const days30End = dateFormat("YYYY-mm-dd", past30daysEnd);
  return [days30Start, days30End];
};
// 获取本周
export const pastWeek = (): string[] => {
  const toData = startOfToday()
  const nowDayOfWeek = new Date().getDay();
  const weekStartData = toData - (nowDayOfWeek - 1) * 24 * 60 * 60 * 1000;
  const weekEndData = toData + (7 - nowDayOfWeek) * 24 * 60 * 60 * 1000;
  const weekStart = dateFormat("YYYY-mm-dd", weekStartData);
  const weekEnd = dateFormat("YYYY-mm-dd", weekEndData);
  return [weekStart, weekEnd];
};
// 获取本月
export const pastMonth = (): string[] => {
  const year = new Date().getFullYear()
  const month =new Date().getMonth()
  const monthStartData = new Date(year, month, 1).getTime()
  const monthEndData = new Date(year, month + 1, 0).getTime() + 24 * 60 * 60 * 1000 - 1
  const monthStart = dateFormat("YYYY-mm-dd", monthStartData);
  const monthEnd = dateFormat("YYYY-mm-dd", monthEndData);
  return [monthStart, monthEnd];
};