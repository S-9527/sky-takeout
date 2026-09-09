<template>
  <div :key="vueRest"
       class="addBrand-container">
    <div :key="restKey"
         class="container">
      <el-form ref="ruleFormRef"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm">
        <div>
          <el-form-item label="菜品名称:"
                        prop="name">
            <el-input v-model="ruleForm.name"
                      placeholder="请填写菜品名称"
                      maxlength="20" />
          </el-form-item>
          <el-form-item label="菜品分类:"
                        prop="categoryId">
            <el-select v-model="ruleForm.categoryId"
                       style="width: 240px"
                       placeholder="请选择菜品分类">
              <el-option v-for="(item, index) in dishList"
                         :key="index"
                         :label="item.name"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="菜品价格:"
                        prop="price">
            <el-input v-model="ruleForm.price"
                      placeholder="请设置菜品价格" />
          </el-form-item>
        </div>
        <el-form-item label="口味做法配置:">
          <el-form-item>
            <div class="flavorBox">
              <span v-if="dishFlavors.length == 0"
                    class="addBut"
                    @click="addFlavore">
                + 添加口味</span>
              <div v-if="dishFlavors.length != 0"
                   class="flavor">
                <div class="title">
                  <span>口味名（3个字内）</span>
                  <!-- <span class="des-box">口味标签（输入标签回车添加）</span> -->
                </div>
                <div class="cont">
                  <div v-for="(item, index) in dishFlavors"
                       :key="index"
                       class="items">
                    <div class="itTit">
                      <!-- :dish-flavors-data="filterDishFlavorsData()" -->
                      <SelectInput :dish-flavors-data="leftDishFlavors"
                                   :index="index"
                                   :value="item.name"
                                   @select="selectHandle" />
                    </div>
                    <div class="labItems"
                         style="display: flex">
                      <span v-for="(it, ind) in item.value"
                            :key="ind">{{ it }}
                        <i @click="delFlavorLabel(index, ind as number)">X</i></span>
                      <div class="inputBox"
                           :style="inputStyle" />
                    </div>
                    <span class="delFlavor delBut non"
                          @click="delFlavor(item.name)">删除</span>
                  </div>
                </div>
                <div v-if="
                       !!leftDishFlavors.length &&
                         dishFlavors.length < dishFlavorsData.length
                     "
                     class="addBut"
                     @click="addFlavore">
                  添加口味
                </div>
              </div>
            </div>
          </el-form-item>
        </el-form-item>
        <div>
          <el-form-item label="菜品图片:"
                        prop="image">
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <div class="address">
          <el-form-item label="菜品描述:"
                        prop="region">
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="菜品描述，最长200字" />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-button @click="() => $router.back()">
            取消
          </el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     @click="submitForm('ruleForm')">
            保存
          </el-button>
          <el-button v-if="actionType == 'add'"
                     type="primary"
                     @click="submitForm('ruleForm', 'goAnd')">
            保存并继续添加
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import SelectInput from './components/SelectInput.vue'
import ImageUpload from '@/components/ImgUpload/index.vue'
// getFlavorList口味列表暂时不做 getDishTypeList
import {
  queryDishById,
  addDish,
  editDish,
  getCategoryList
} from '@/api/dish'

const route = useRoute()
const router = useRouter()

const restKey = ref(0)
const imageUrl = ref('')
const actionType = ref('')
const dishList = ref<any[]>([])
const dishFlavorsData = ref<any[]>([]) //原始口味数据
const dishFlavors = ref<any[]>([]) //待上传口味的数据
const leftDishFlavors = ref<any[]>([]) //下拉框剩余可选择的口味数据
const vueRest = ref('1')
const inputStyle = ref({ flex: 1 })
const ruleForm = ref<any>({
  name: '',
  id: '',
  price: '',
  code: '',
  image: '',
  description: '',
  dishFlavors: [],
  status: true,
  categoryId: ''
})
const ruleFormRef = ref<FormInstance>()

const rules = computed(() => {
  return {
    name: [
      {
        required: true,
        validator: (_rule: any, value: string, callback: Function) => {
          if (!value) {
            callback(new Error('请输入菜品名称'))
          } else {
            const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
            if (!reg.test(value)) {
              callback(new Error('菜品名称输入不符，请输入2-20个字符'))
            } else {
              callback()
            }
          }
        },
        trigger: 'blur'
      }
    ],
    categoryId: [
      { required: true, message: '请选择菜品分类', trigger: 'change' }
    ],
    image: {
      required: true,
      message: '菜品图片不能为空'
    },
    price: [
      {
        required: true,
        // 'message': '请填写菜品价格',
        validator: (_rules: any, value: string, callback: Function) => {
          const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
          if (!reg.test(value) || Number(value) <= 0) {
            callback(
              new Error(
                '菜品价格格式有误，请输入大于零且最多保留两位小数的金额'
              )
            )
          } else {
            callback()
          }
        },
        trigger: 'blur'
      }
    ],
    code: [{ required: true, message: '请填写商品码', trigger: 'blur' }]
  }
})

getDishList()
// 口味临时数据
getFlavorListHand()
actionType.value = route.query.id ? 'edit' : 'add'
if (route.query.id) {
  init()
}

watch(dishFlavors, () => {
  getLeftDishFlavors()
})

//过滤已选择的口味下拉框无法再次选择
const getLeftDishFlavors = () => {
  let arr: any[] = []
  dishFlavorsData.value.map(item => {
    if (
      dishFlavors.value.findIndex(item1 => item.name === item1.name) === -1
    ) {
      arr.push(item)
    }
  })
  leftDishFlavors.value = arr
}

const selectHandle = (val: any, key: any, _ind: any) => {
  const arrDate = [...dishFlavors.value]
  const idx = dishFlavorsData.value.findIndex(item => item.name === val)
  arrDate[key] = JSON.parse(JSON.stringify(dishFlavorsData.value[idx]))
  dishFlavors.value = arrDate
}

async function init() {
  queryDishById(String(route.query.id)).then(res => {
    ruleForm.value = { ...res }
    ruleForm.value.price = String(res.price)
    ruleForm.value.status = res.status == '1'
    dishFlavors.value =
      res.flavors &&
      res.flavors.map((obj: any) => ({
        ...obj,
        value: JSON.parse(obj.value)
      }))
    getLeftDishFlavors()
    imageUrl.value = res.image
  })
}

// 按钮 - 添加口味
const addFlavore = () => {
  dishFlavors.value.push({ name: '', value: [] }) // JSON.parse(JSON.stringify(this.dishFlavorsData))
}

// 按钮 - 删除口味
const delFlavor = (name: string) => {
  let ind = dishFlavors.value.findIndex(item => item.name === name)
  dishFlavors.value.splice(ind, 1)
}

// 按钮 - 删除口味标签
const delFlavorLabel = (index2: number, ind: number) => {
  dishFlavors.value[index2].value.splice(ind, 1)
}

// 获取菜品分类
function getDishList() {
  getCategoryList({ type: 1 }).then(res => {
    dishList.value = res
  })
}

// 获取口味列表
function getFlavorListHand() {
  // flavor flavorData
  dishFlavorsData.value = [
    { name: '甜味', value: ['无糖', '少糖', '半糖', '多糖', '全糖'] },
    { name: '温度', value: ['热饮', '常温', '去冰', '少冰', '多冰'] },
    { name: '忌口', value: ['不要葱', '不要蒜', '不要香菜', '不要辣'] },
    { name: '辣度', value: ['不辣', '微辣', '中辣', '重辣'] }
  ]
}

const submitForm = (_formName: any, st?: any) => {
  ;(ruleFormRef.value as any).validate((valid: any) => {
    console.log(valid, 'valid')
    if (valid) {
      if (!ruleForm.value.image) return ElMessage.error('菜品图片不能为空')
      let params: any = { ...ruleForm.value }
      // params.flavors = this.dishFlavors
      params.status =
        actionType.value === 'add' ? 0 : ruleForm.value.status ? 1 : 0
      // params.price *= 100
      params.categoryId = ruleForm.value.categoryId
      params.flavors = dishFlavors.value.map(obj => ({
        ...obj,
        value: JSON.stringify(obj.value)
      }))
      delete params.dishFlavors
      if (actionType.value == 'add') {
        delete params.id
        addDish(params)
          .then(() => {
            ElMessage.success('菜品添加成功！')
            if (!st) {
              router.push({ path: '/dish' })
            } else {
              dishFlavors.value = []
              // this.dishFlavorsData = []
              imageUrl.value = ''
              ruleForm.value = {
                name: '',
                id: '',
                price: '',
                code: '',
                image: '',
                description: '',
                dishFlavors: [],
                status: true,
                categoryId: ''
              }
              restKey.value++
            }
          })
          .catch(err => {
            ElMessage.error('请求出错了：' + err.message)
          })
      } else {
        delete params.createTime
        delete params.updateTime
        editDish(params)
          .then(() => {
            router.push({ path: '/dish' })
            ElMessage.success('菜品修改成功！')
          })
          .catch(err => {
            ElMessage.error('请求出错了：' + err.message)
          })
      }
    } else {
      return false
    }
  })
}

const imageChange = (value: any) => {
  ruleForm.value.image = value
}
</script>
<style lang="scss" scoped>
.addBrand-container {
  .el-form--inline .el-form-item__content {
    width: 293px;
  }

  .el-input {
    width: 350px;
  }

  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
}
</style>
<style lang="scss" scoped>
.addBrand {
  &-container {
    margin: 30px;

    .container {
      position: relative;
      z-index: 1;
      background: #fff;
      padding: 30px;
      border-radius: 4px;
      min-height: 500px;

      .subBox {
        padding-top: 30px;
        text-align: center;
        border-top: solid 1px $gray-5;
      }
      .upload-item {
        .el-form-item__error {
          top: 90%;
        }
      }
    }
  }
}

.flavorBox {
  width: 777px;

  .addBut {
    background: #ffc200;
    display: inline-block;
    padding: 0px 20px;
    border-radius: 3px;
    line-height: 40px;
    cursor: pointer;
    border-radius: 4px;
    color: #333333;
    font-weight: 500;
  }

  .flavor {
    border: solid 1px #dfe2e8;
    border-radius: 3px;
    padding: 15px;
    background: #fafafb;

    .title {
      color: #606168;
      .des-box {
        padding-left: 44px;
      }
    }

    .cont {
      .items {
        display: flex;
        margin: 10px 0;

        .itTit {
          width: 150px;
          margin-right: 15px;

          input {
            width: 100%;
            // line-height: 40px;
            // border-radius: 3px;
            // padding: 0 10px;
          }
        }

        .labItems {
          flex: 1;
          display: flex;
          flex-wrap: wrap;
          border-radius: 3px;
          min-height: 39px;
          border: solid 1px #d8dde3;
          background: #fff;
          padding: 0 5px;

          span {
            display: inline-block;
            color: #ffc200;
            margin: 5px;
            line-height: 26px;
            padding: 0 10px;
            background: #fffbf0;
            border: 1px solid #fbe396;
            border-radius: 4px;
            font-size: 12px;

            i {
              cursor: pointer;
              font-style: normal;
            }
          }

          .inputBox {
            display: inline-block;
            width: 100%;
            height: 36px;
            line-height: 36px;
            overflow: hidden;
          }
        }

        .delFlavor {
          display: inline-block;
          padding: 0 10px;
          color: #f19c59;
          cursor: pointer;
        }
      }
    }
  }
}
</style>
