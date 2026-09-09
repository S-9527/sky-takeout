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
                       style="width: 240px"
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
                          {{ (Number(Number(scope.row.price).toFixed(2)) * 100) / 100 }}
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
                          @image-change="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
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
               v-model="dialogVisible"
               title="添加菜品"
               class="addDishList"
               width="60%"
               :before-close="handleClose">
      <el-input v-model="value"
                class="seachDish"
                placeholder="请输入菜品名称进行搜索"
                style="width: 293px; height: 40px"
                size="small"
                clearable>
        <template #prefix>
          <el-icon class="el-input__icon"
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
               @check-list="getCheckList" />
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
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AddDish from './components/AddDish.vue'
import ImageUpload from '@/components/ImgUpload/index.vue'
import { querySetmealById, addSetmeal, editSetmeal } from '@/api/setMeal'
import { getCategoryList } from '@/api/category'
import type { Category } from '@/api/types/category'
import type { SetmealDish, SetmealDTO } from '@/api/types/setmeal'
import { amountRule, nameRule } from '@/utils/formRules'

const route = useRoute()
const router = useRouter()

const value = ref<string>('')
const setMealList = ref<(Category & { idType: number })[]>([])
const seachKey = ref<string>('')
const dishList = ref<SetmealDish[]>([])
const imageUrl = ref<string>('')
const actionType = ref<string>('')
const dishTable = ref<SetmealDish[]>([])
const dialogVisible = ref<boolean>(false)
const checkList = ref<SetmealDish[]>([])
const ruleForm = ref<{
  name: string
  categoryId: string
  price: string
  code: string
  image: string
  description: string
  status: boolean
  idType: number | ''
}>({
  name: '',
  categoryId: '',
  price: '',
  code: '',
  image: '',
  description: '',
  status: true,
  idType: ''
})
const ruleFormRef = ref<FormInstance>()

const rules: FormRules = {
  name: [nameRule('套餐名称', '请输入套餐名称')],
  idType: { required: true, message: '请选择套餐分类', trigger: 'change' },
  image: { required: true, message: '菜品图片不能为空' },
  price: [amountRule('套餐价格')],
  code: { required: true, message: '请输入商品码', trigger: 'blur' }
}

const init = async () => {
  querySetmealById(Number(route.query.id)).then(res => {
    ruleForm.value = {
      name: res.name,
      categoryId: String(res.categoryId),
      price: String(res.price),
      code: '',
      image: res.image,
      description: res.description ?? '',
      status: res.status === 1,
      idType: res.categoryId
    }
    imageUrl.value = res.image
    checkList.value = res.setmealDishes
    dishTable.value = res.setmealDishes.slice().reverse()
  })
}

const seachHandle = () => {
  seachKey.value = value.value
}

// 获取套餐分类
const getDishTypeList = () => {
  getCategoryList({ type: 2 }).then(res => {
    setMealList.value = res.map(obj => ({
      ...obj,
      idType: obj.id
    }))
  })
}

// 删除套餐菜品
const delDishHandle = (index: number) => {
  dishTable.value.splice(index, 1)
  checkList.value = dishTable.value
}

// 获取添加菜品数据 - 确定加菜倒序展示
const getCheckList = (val: SetmealDish[]) => {
  checkList.value = [...val].reverse()
}

// 添加菜品
const openAddDish = (_st: string) => {
  seachKey.value = ''
  dialogVisible.value = true
}

// 取消添加菜品
const handleClose = (_done: () => void) => {
  dialogVisible.value = false
  checkList.value = JSON.parse(JSON.stringify(dishTable.value))
}

// 保存添加菜品列表
const addTableList = () => {
  dishTable.value = JSON.parse(JSON.stringify(checkList.value))
  dishTable.value.forEach(n => {
    n.copies = 1
  })
  dialogVisible.value = false
}

const submitForm = (_formName: string, st: boolean) => {
  ruleFormRef.value?.validate((valid) => {
    if (valid) {
      if (dishTable.value.length === 0) {
        ElMessage.error('套餐下菜品不能为空')
        return
      }
      if (!ruleForm.value.image) {
        ElMessage.error('套餐图片不能为空')
        return
      }
      const prams: SetmealDTO = {
        name: ruleForm.value.name,
        categoryId: ruleForm.value.idType as number,
        price: Number(ruleForm.value.price),
        image: ruleForm.value.image,
        description: ruleForm.value.description,
        status: actionType.value === 'add' ? 0 : ruleForm.value.status ? 1 : 0,
        setmealDishes: dishTable.value.map(obj => ({
          dishId: obj.dishId,
          name: obj.name,
          price: obj.price,
          copies: obj.copies
        }))
      }
      if (actionType.value == 'add') {
        addSetmeal(prams)
          .then(() => {
            ElMessage.success('套餐添加成功！')
            if (!st) {
              router.push({ path: '/setmeal' })
            } else {
              ruleFormRef.value?.resetFields()
              dishList.value = []
              dishTable.value = []
              ruleForm.value = {
                name: '',
                categoryId: '',
                price: '',
                code: '',
                image: '',
                description: '',
                status: true,
                idType: ''
              }
              imageUrl.value = ''
            }
          })
      } else {
        editSetmeal({ ...prams, id: Number(route.query.id) })
          .then(() => {
            ElMessage.success('套餐修改成功！')
            router.push({ path: '/setmeal' })
          })
      }
    }
  })
}

const imageChange = (value: string) => {
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

  /* EP2 把 .el-form-item__content 改成 flex,.subBox 的 text-align: center 对它失效,
     底部按钮组因此贴左,这里补 flex 居中(按钮本身尺寸不动) */
  .subBox .el-form-item__content {
    justify-content: center;
  }

  .el-input__prefix {
    top: 2px;
  }

  .addDish {
    .el-input {
      width: 130px;
    }

    .el-input-number {
      width: 130px;
    }

    /* EP2 的 input-number 外框由 wrapper 的 inset 阴影绘制,聚焦/悬浮会换色出现一圈描边;
       Element UI 聚焦无任何变化,这里固定为黄框 */
    .el-input-number .el-input {
      --el-input-height: 32px;
      --el-input-inner-height: 30px;
    }

    .el-input-number .el-input__wrapper {
      box-shadow: 0 0 0 1px #fbe396 inset !important;
      font-size: 13px;
    }

    .el-input-number .el-input__inner {
      border: none;
    }

    .el-input-number__increase {
      border-left: solid 1px #fbe396;
      background: #fffbf0;
    }

    .el-input-number__decrease {
      border-right: solid 1px #fbe396;
      background: #fffbf0;
    }

    .table {
      border: solid 1px #ebeef5;
      border-radius: 3px;

      /* EP2 的 .el-form-item__content line-height 是 32px(Element UI 为 40px),
         表头/行高因此比原版矮,这里按原版尺寸补齐 */
      th {
        padding: 5px 0;
        height: 51px;
      }

      td {
        padding: 7px 0;
        height: 48px;
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
