<template>
  <div class="addBrand-container">
    <div class="container">
      <el-form ref="ruleFormRef"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm">
        <div>
          <el-form-item label="套餐名称:"
                        prop="name">
            <el-input v-model="ruleForm.name"
                      placeholder="请填写套餐名称"
                      maxlength="14" />
          </el-form-item>
          <el-form-item label="套餐分类:"
                        prop="idType">
            <el-select v-model="ruleForm.idType"
                       placeholder="请选择套餐分类"
                       @change="$forceUpdate()">
              <el-option v-for="(item, index) in setMealList"
                         :key="index"
                         :label="item.name"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="套餐价格:"
                        prop="price">
            <el-input v-model="ruleForm.price"
                      placeholder="请设置套餐价格" />
          </el-form-item>
        </div>
        <div>
          <el-form-item label="套餐菜品:"
                        required>
            <el-form-item>
              <div class="addDish">
                <span v-if="dishTable.length == 0"
                      class="addBut"
                      @click="openAddDish('new')">
                  + 添加菜品</span>
                <div v-if="dishTable.length != 0"
                     class="content">
                  <div class="addBut"
                       style="margin-bottom: 20px"
                       @click="openAddDish('change')">
                    + 添加菜品
                  </div>
                  <div class="table">
                    <el-table :data="dishTable"
                              style="width: 100%">
                      <el-table-column prop="name"
                                       label="名称"
                                       width="180"
                                       align="center" />
                      <el-table-column prop="price"
                                       label="原价"
                                       width="180"
                                       align="center">
                        <template #default="scope">
                          {{ (Number(scope.row.price).toFixed(2) * 100) / 100 }}
                        </template>
                      </el-table-column>
                      <el-table-column prop="address"
                                       label="份数"
                                       align="center">
                        <template #default="scope">
                          <el-input-number v-model="scope.row.copies"
                                           size="small"
                                           :min="1"
                                           :max="99"
                                           label="描述文字" />
                        </template>
                      </el-table-column>
                      <el-table-column prop="address"
                                       label="操作"
                                       width="180px;"
                                       align="center">
                        <template #default="scope">
                          <el-button link
                                     size="small"
                                     class="delBut non"
                                     @click="delDishHandle(scope.$index)">
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </div>
              </div>
            </el-form-item>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="套餐图片:"
                        required
                        prop="image">
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <!-- <el-form-item
          label="商品码:"
          prop="code"
        >
          <el-input
            v-model="ruleForm.code"
            placeholder="请输入商品码"
          />
        </el-form-item> -->
        <div class="address">
          <el-form-item label="套餐描述:">
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="套餐描述，最长200字" />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-form-item>
            <el-button @click="() => $router.back()">
              取消
            </el-button>
            <el-button type="primary"
                       :class="{ continue: actionType === 'add' }"
                       @click="submitForm('ruleForm', false)">
              保存
            </el-button>
            <el-button v-if="actionType == 'add'"
                       type="primary"
                       @click="submitForm('ruleForm', true)">
              保存并继续添加
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>
    <el-dialog v-if="dialogVisible"
               title="添加菜品"
               class="addDishList"
               v-model="dialogVisible"
               width="60%"
               :before-close="handleClose">
      <el-input v-model="value"
                class="seachDish"
                placeholder="请输入菜品名称进行搜索"
                style="width: 293px; height: 40px"
                size="small"
                clearable>
        <template #prefix>
          <el-icon class="el-input__icon el-icon-search"
                   style="cursor: pointer"
                   @click="seachHandle">
            <Search />
          </el-icon>
        </template>
      </el-input>
      <AddDish v-if="dialogVisible"
               ref="adddish"
               :check-list="checkList"
               :seach-key="seachKey"
               :dish-list="dishList"
               @checkList="getCheckList" />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleClose">取 消</el-button>
          <el-button type="primary"
                     @click="addTableList">添 加</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import ImgUpload from '@/components/ImgUpload/index.vue'
import AddDish from './components/AddDish.vue'
import { querySetmealById, addSetmeal, editSetmeal } from '@/api/setMeal'
import { getCategoryList } from '@/api/dish'

const route = useRoute()
const router = useRouter()

const value = ref<string>('')
const setMealList = ref<any[]>([])
const seachKey = ref<string>('')
const dishList = ref<any[]>([])
const imageUrl = ref<string>('')
const actionType = ref<string>('')
const dishTable = ref<any[]>([])
const dialogVisible = ref<boolean>(false)
const checkList = ref<any[]>([])
const ruleForm = ref({
  name: '',
  categoryId: '',
  price: '',
  code: '',
  image: '',
  description: '',
  dishList: [],
  status: true,
  idType: ''
})
const ruleFormRef = ref<any>()

const rules = computed(() => {
  return {
    name: {
      required: true,
      validator: (rule: any, value: string, callback: Function) => {
        if (!value) {
          callback(new Error('请输入套餐名称'))
        } else {
          const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
          if (!reg.test(value)) {
            callback(new Error('套餐名称输入不符，请输入2-20个字符'))
          } else {
            callback()
          }
        }
      },
      trigger: 'blur'
    },
    idType: {
      required: true,
      message: '请选择套餐分类',
      trigger: 'change'
    },
    image: {
      required: true,
      message: '菜品图片不能为空'
    },
    price: {
      required: true,
      validator: (rules: any, value: string, callback: Function) => {
        const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
        if (!reg.test(value) || Number(value) <= 0) {
          callback(
            new Error(
              '套餐价格格式有误，请输入大于零且最多保留两位小数的金额'
            )
          )
        } else {
          callback()
        }
      },
      trigger: 'blur'
    },
    code: { required: true, message: '请输入商品码', trigger: 'blur' }
  }
})

const init = async () => {
  querySetmealById(route.query.id).then(res => {
    if (res && res.data && res.data.code === 1) {
      ruleForm.value = res.data.data
      ruleForm.value.status = res.data.data.status == '1'
      ;(ruleForm.value as any).price = res.data.data.price
      imageUrl.value = res.data.data.image
      checkList.value = res.data.data.setmealDishes
      dishTable.value = res.data.data.setmealDishes.reverse()
      ruleForm.value.idType = res.data.data.categoryId
    } else {
      ElMessage.error(res.data.msg)
    }
  })
}

const seachHandle = () => {
  seachKey.value = value.value
}

// 获取套餐分类
const getDishTypeList = () => {
  getCategoryList({ type: 2, page: 1, pageSize: 1000 }).then(res => {
    if (res && res.data && res.data.code === 1) {
      setMealList.value = res.data.data.map((obj: any) => ({
        ...obj,
        idType: obj.id
      }))
    } else {
      ElMessage.error(res.data.msg)
    }
  })
}

// 删除套餐菜品
const delDishHandle = (index: any) => {
  dishTable.value.splice(index, 1)
  checkList.value = dishTable.value
}

// 获取添加菜品数据 - 确定加菜倒序展示
const getCheckList = (value: any) => {
  checkList.value = [...value].reverse()
}

// 添加菜品
const openAddDish = (st: string) => {
  seachKey.value = ''
  dialogVisible.value = true
}

// 取消添加菜品
const handleClose = (done: any) => {
  dialogVisible.value = false
  checkList.value = JSON.parse(JSON.stringify(dishTable.value))
}

// 保存添加菜品列表
const addTableList = () => {
  dishTable.value = JSON.parse(JSON.stringify(checkList.value))
  dishTable.value.forEach((n: any) => {
    n.copies = 1
  })
  dialogVisible.value = false
}

const submitForm = (formName: any, st: any) => {
  ;(ruleFormRef.value as any).validate((valid: any) => {
    if (valid) {
      if (dishTable.value.length === 0) {
        return ElMessage.error('套餐下菜品不能为空')
      }
      if (!ruleForm.value.image) return ElMessage.error('套餐图片不能为空')
      let prams = { ...ruleForm.value } as any
      prams.setmealDishes = dishTable.value.map((obj: any) => ({
        copies: obj.copies,
        dishId: obj.dishId,
        name: obj.name,
        price: obj.price
      }))
      ;(prams as any).status =
        actionType.value === 'add' ? 0 : ruleForm.value.status ? 1 : 0
      prams.categoryId = ruleForm.value.idType
      if (actionType.value == 'add') {
        delete prams.id
        addSetmeal(prams)
          .then(res => {
            if (res && res.data && res.data.code === 1) {
              ElMessage.success('套餐添加成功！')
              if (!st) {
                router.push({ path: '/setmeal' })
              } else {
                ;(ruleFormRef.value as any).resetFields()
                dishList.value = []
                dishTable.value = []
                ruleForm.value = {
                  name: '',
                  categoryId: '',
                  price: '',
                  code: '',
                  image: '',
                  description: '',
                  dishList: [],
                  status: true,
                  id: '',
                  idType: ''
                } as any
                imageUrl.value = ''
              }
            } else {
              ElMessage.error(res.data.msg)
            }
          })
          .catch(err => {
            ElMessage.error('请求出错了：' + err.message)
          })
      } else {
        delete prams.updateTime
        editSetmeal(prams)
          .then(res => {
            if (res.data.code === 1) {
              ElMessage.success('套餐修改成功！')
              router.push({ path: '/setmeal' })
            } else {
              // ElMessage.error(res.data.desc || res.data.message)
            }
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

getDishTypeList()
actionType.value = route.query.id ? 'edit' : 'add'
if (actionType.value == 'edit') {
  init()
}
</script>
<style>
.avatar-uploader .el-icon-plus:after {
  position: absolute;
  display: inline-block;
  content: ' ' !important;
  left: calc(50% - 20px);
  top: calc(50% - 40px);
  width: 40px;
  height: 40px;
  background: url('./../../assets/icons/icon_upload@2x.png') center center
    no-repeat;
  background-size: 20px;
}
</style>
<style lang="scss">
// .el-form-item__error {
//   top: 90%;
// }
.addBrand-container {
  .avatar-uploader .el-upload {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
  }

  .avatar-uploader .el-upload:hover {
    border-color: #ffc200;
  }

  .avatar-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 200px;
    height: 160px;
    line-height: 160px;
    text-align: center;
  }

  .avatar {
    width: 200px;
    height: 160px;
    display: block;
  }

  // .el-form--inline .el-form-item__content {
  //   width: 293px;
  // }

  .el-input {
    width: 293px;
  }

  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
  .el-input__prefix {
    top: 2px;
  }

  .addDish {
    .el-input {
      width: 130px;
    }

    .el-input-number__increase {
      border-left: solid 1px #fbe396;
      background: #fffbf0;
    }

    .el-input-number__decrease {
      border-right: solid 1px #fbe396;
      background: #fffbf0;
    }

    input {
      border: 1px solid #fbe396;
    }

    .table {
      border: solid 1px #ebeef5;
      border-radius: 3px;

      th {
        padding: 5px 0;
      }

      td {
        padding: 7px 0;
      }
    }
  }

  .addDishList {
    .seachDish {
      position: absolute;
      top: 12px;
      right: 20px;
    }

    .el-dialog__footer {
      padding-top: 27px;
    }

    .el-dialog__body {
      padding: 0;
      border-bottom: solid 1px #efefef;
    }
    .seachDish {
      .el-input__inner {
        height: 40px;
        line-height: 40px;
      }
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
      .el-input {
        width: 350px;
      }
      .addDish {
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

        .content {
          background: #fafafb;
          padding: 20px;
          border: solid 1px #d8dde3;
          border-radius: 3px;
        }
      }
    }
  }
}
</style>
