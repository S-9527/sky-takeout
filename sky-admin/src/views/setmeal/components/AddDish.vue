<template>
  <div class="addDish">
    <div class="leftCont">
      <div v-show="seachKey.trim() == ''"
           class="tabBut">
        <span v-for="(item, index) in dishType"
              :key="index"
              :class="{ act: index == keyInd }"
              @click="checkTypeHandle(index, item.id)">{{ item.name }}</span>
      </div>
      <div class="tabList">
        <div class="table"
             :class="{ borderNone: !dishList.length }">
          <div v-if="dishList.length == 0"
               style="padding-left: 10px">
            <Empty />
          </div>
          <el-checkbox-group v-if="dishList.length > 0"
                             v-model="checkedList"
                             @change="checkedListHandle">
            <div v-for="(item, index) in dishList"
                 :key="item.name + item.id"
                 class="items">
              <el-checkbox :key="index"
                           :label="item.name">
                <div class="item">
                  <span style="flex: 3; text-align: left">{{
                    item.dishName
                  }}</span>
                  <span>{{ item.status == 0 ? '停售' : '在售' }}</span>
                  <span>{{ (Number(item.price) ).toFixed(2)*100/100 }}</span>
                </div>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
      </div>
    </div>
    <div class="ritCont">
      <div class="tit">
        已选菜品({{ checkedListAll.length }})
      </div>
      <div class="items">
        <div v-for="(item, ind) in checkedListAll"
             :key="ind"
             class="item">
          <span>{{ item.dishName || item.name }}</span>
          <span class="price">￥ {{ (Number(item.price) ).toFixed(2)*100/100 }} </span>
          <span class="del"
                @click="delCheck(item.name)">
            <img src="./../../../assets/icons/btn_clean@2x.png"
                 alt="">
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { PropType } from 'vue'
import { getCategoryList, queryDishList } from '@/api/dish'
import { ElMessage } from 'element-plus'
import Empty from '@/components/Empty/index.vue'

const props = defineProps({
  value: { type: Number, default: '' },
  checkList: { type: Array as PropType<any[]>, default: () => [] },
  seachKey: { type: String, default: '' }
})

const emit = defineEmits(['checkList'])

const dishType = ref<any[]>([])
const dishList = ref<any[]>([])
const allDishList = ref<any[]>([])
const dishListCache = ref<any[]>([])
const keyInd = ref(0)
const searchValue = ref<string>('')
const checkedList = ref<any[]>([])
const checkedListAll = ref<any[]>([])
const ids = ref<any>(new Set())

watch(
  () => props.seachKey,
  (value: any) => {
    if (value.trim()) {
      getDishForName(props.seachKey)
    }
  }
)

const getDishType = () => {
  getCategoryList({ type: 1 }).then(res => {
    if (res && res.data && res.data.code === 1) {
      dishType.value = res.data.data
      getDishList(res.data.data[0].id)
    } else {
      ElMessage.error(res.data.msg)
    }
  })
}

// 通过套餐ID获取菜品列表分类
const getDishList = (id: number) => {
  queryDishList({ categoryId: id }).then(res => {
    if (res && res.data && res.data.code === 1) {
      if (res.data.data.length == 0) {
        dishList.value = []
        return
      }
      let newArr = res.data.data
      newArr.forEach((n: any) => {
        n.dishId = n.id
        n.copies = 1
        n.dishName = n.name
      })
      dishList.value = newArr
      if (!ids.value.has(id)) {
        allDishList.value = [...allDishList.value, ...newArr]
      }
      ids.value.add(id)
    } else {
      ElMessage.error(res.data.msg)
    }
  })
}

// 关键词收搜菜品列表分类
const getDishForName = (name: any) => {
  queryDishList({ name }).then(res => {
    if (res && res.data && res.data.code === 1) {
      let newArr = res.data.data
      newArr.forEach((n: any) => {
        n.dishId = n.id
        n.dishName = n.name
      })
      dishList.value = newArr
    } else {
      ElMessage.error(res.data.msg)
    }
  })
}

// 点击分类
const checkTypeHandle = (ind: number, id: any) => {
  keyInd.value = ind
  getDishList(id)
}

// 添加菜品
const checkedListHandle = (value: [string]) => {
  checkedListAll.value.reverse()
  const list = allDishList.value.filter((item: any) => {
    let data
    value.forEach((it: any) => {
      if (item.name == it) {
        data = item
      }
    })
    return data
  })
  const dishListCat = [...checkedListAll.value, ...list]
  let arrData: any[] = []
  checkedListAll.value = dishListCat.filter((item: any) => {
    let allArrDate
    if (arrData.length == 0) {
      arrData.push(item.name)
      allArrDate = item
    } else {
      const st = arrData.some(it => item.name == it)
      if (!st) {
        arrData.push(item.name)
        allArrDate = item
      }
    }
    return allArrDate
  })
  if (value.length < arrData.length) {
    checkedListAll.value = checkedListAll.value.filter((item: any) => {
      if (value.some(it => it == item.name)) {
        return item
      }
    })
  }
  emit('checkList', checkedListAll.value)
  checkedListAll.value.reverse()
}

const init = () => {
  getDishType()
  checkedList.value = props.checkList.map((it: any) => it.name)
  checkedListAll.value = props.checkList.reverse()
}

const open = (done: any) => {
  dishListCache.value = JSON.parse(JSON.stringify(props.checkList))
}

const close = (done: any) => {
  ;(props as any).checkList = dishListCache.value
}

// 删除
const delCheck = (name: any) => {
  const index = checkedList.value.findIndex(it => it === name)
  const indexAll = checkedListAll.value.findIndex(
    (it: any) => it.name === name
  )

  checkedList.value.splice(index, 1)
  checkedListAll.value.splice(indexAll, 1)
  emit('checkList', checkedListAll.value)
}

init()
</script>
<style lang="scss">
.addDish {
  .el-checkbox__label {
    width: 100%;
  }
  .empty-box {
    margin-top: 50px;
    margin-bottom: 0px;
  }
}
</style>
<style lang="scss" scoped>
.addDish {
  padding: 0 20px;
  display: flex;
  line-height: 40px;
  .empty-box {
    img {
      width: 190px;
      height: 147px;
    }
  }

  .borderNone {
    border: none !important;
  }
  span,
  .tit {
    color: #333;
  }
  .leftCont {
    display: flex;
    border-right: solid 1px #efefef;
    width: 60%;
    padding: 15px;
    .tabBut {
      width: 110px;
      font-weight: bold;
      border-right: solid 2px #f4f4f4;
      span {
        display: block;
        text-align: center;
        // border-right: solid 2px #f4f4f4;
        cursor: pointer;
        position: relative;
      }
    }
    .act {
      border-color: $mine !important;
      color: $mine !important;
    }
    .act::after {
      content: ' ';
      display: inline-block;
      background-color: $mine;
      width: 2px;
      height: 40px;
      position: absolute;
      right: -2px;
    }
    .tabList {
      flex: 1;
      padding: 15px;
      height: 400px;
      overflow-y: scroll;
      .table {
        border: solid 1px #f4f4f4;
        border-bottom: solid 1px #f4f4f4;
        .items {
          border-bottom: solid 1px #f4f4f4;
          padding: 0 10px;
          display: flex;
          .el-checkbox,
          .el-checkbox__label {
            width: 100%;
          }
          .item {
            display: flex;
            padding-right: 20px;
            span {
              display: inline-block;
              text-align: center;
              flex: 1;
              font-weight: normal;
            }
          }
        }
      }
    }
  }
  .ritCont {
    width: 40%;
    .tit {
      margin: 0 15px;
      font-weight: bold;
    }
    .items {
      height: 338px;
      padding: 4px 15px;
      overflow: scroll;
    }
    .item {
      box-shadow: 0px 1px 4px 3px rgba(0, 0, 0, 0.03);
      display: flex;
      text-align: center;
      padding: 0 10px;
      margin-bottom: 20px;
      border-radius: 6px;
      color: #818693;
      span:first-child {
        text-align: left;
        color: #20232a;
        flex: 70%;
      }
      .price {
        display: inline-block;
        flex: 70%;
        text-align: left;
      }
      .del {
        cursor: pointer;
        img {
          position: relative;
          top: 5px;
          width: 20px;
        }
      }
    }
  }
}
</style>
