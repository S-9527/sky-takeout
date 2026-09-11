import { afterAll, beforeAll, describe, expect, it } from 'vitest'

import * as categoryApi from '@/api/category'
import * as dishApi from '@/api/dish'
import * as setmealApi from '@/api/setmeal'
import type { Category, DishDetail, SetmealDetail } from '@/types'

import { expectApiError, expectOk, loginAsAdmin } from './helpers'

/**
 * 商品管理的契约集成测试:分类 / 菜品 / 套餐。
 *
 * 覆盖三类只有真后端才给得出的东西:
 * 1. **路径与方法**(前端 api 层写错就红);
 * 2. **业务规则的错误码**(重名 409、类型不可改 422、被引用不可删 422……);
 * 3. **往返一致性**(口味选项、套餐组成写进去再读出来是否一样)。
 *
 * 自造数据全部在收尾时删除,若中途失败也会由 afterAll 兜底 —— 种子数据不动。
 */

const stamp = Date.now().toString(36)
const created = {
  categories: [] as number[],
  dishes: [] as number[],
  setmeals: [] as number[],
}

async function safeDelete(kind: keyof typeof created, id: number): Promise<void> {
  try {
    if (kind === 'categories') await categoryApi.deleteCategory(id)
    if (kind === 'dishes') await dishApi.deleteDishes([id])
    if (kind === 'setmeals') await setmealApi.deleteSetmeals([id])
  } catch {
    // 已经删掉/被引用了都不影响收尾
  }
}

beforeAll(async () => {
  await loginAsAdmin()
})

afterAll(async () => {
  // 顺序反过来:先套餐,再菜品,最后分类(有外键 RESTRICT)
  for (const id of created.setmeals) await safeDelete('setmeals', id)
  for (const id of created.dishes) await safeDelete('dishes', id)
  for (const id of created.categories) await safeDelete('categories', id)
})

describe('分类', () => {
  it('新增 → 出现在选项里 → 编辑 → 启停用 → 删除', async () => {
    const name = `集成分类-${stamp}`
    const category: Category = await expectOk(() =>
      categoryApi.createCategory({ name, type: 'DISH', sortOrder: 900, status: 1 }),
    )
    created.categories.push(category.id)
    expect(category.id).toBeGreaterThan(0)
    expect(category.type).toBe('DISH')

    const options = await expectOk(() => categoryApi.listCategoryOptions('DISH', true))
    expect(options.some((item) => item.id === category.id)).toBe(true)

    const updated = await expectOk(() =>
      categoryApi.updateCategory(category.id, { name: `${name}-改`, sortOrder: 901, status: 1 }),
    )
    expect(updated.name).toBe(`${name}-改`)
    expect(updated.sortOrder).toBe(901)

    await expectOk(() => categoryApi.changeCategoryStatus(category.id, 0))
    const disabled = await expectOk(() => categoryApi.getCategory(category.id))
    expect(disabled.status).toBe(0)

    // 默认只返回启用中的分类,禁用后就不在默认列表里了
    const enabledOnly = await expectOk(() => categoryApi.listCategoryOptions('DISH'))
    expect(enabledOnly.some((item) => item.id === category.id)).toBe(false)

    await expectOk(() => categoryApi.deleteCategory(category.id))
    created.categories = created.categories.filter((id) => id !== category.id)
    await expectApiError(() => categoryApi.getCategory(category.id), {
      status: 404,
      code: 'CATEGORY_NOT_FOUND',
    })
  })

  it('同类型重名 → 409 CATEGORY_NAME_TAKEN', async () => {
    const name = `集成重名-${stamp}`
    const first = await expectOk(() => categoryApi.createCategory({ name, type: 'DISH', sortOrder: 0 }))
    created.categories.push(first.id)

    await expectApiError(() => categoryApi.createCategory({ name, type: 'DISH', sortOrder: 0 }), {
      status: 409,
      code: 'CATEGORY_NAME_TAKEN',
    })

    // 不同类型下同名是允许的
    const setmealCategory = await expectOk(() =>
      categoryApi.createCategory({ name, type: 'SETMEAL', sortOrder: 0 }),
    )
    created.categories.push(setmealCategory.id)
  })

  it('改分类类型 → 422 CATEGORY_TYPE_IMMUTABLE', async () => {
    const category = await expectOk(() =>
      categoryApi.createCategory({ name: `集成类型-${stamp}`, type: 'DISH', sortOrder: 0 }),
    )
    created.categories.push(category.id)

    await expectApiError(
      () => categoryApi.updateCategory(category.id, { name: category.name, type: 'SETMEAL', sortOrder: 0, status: 1 }),
      { status: 422, code: 'CATEGORY_TYPE_IMMUTABLE' },
    )
  })

  it('分页查询支持名称模糊与类型筛选,排序字段受白名单限制', async () => {
    const page = await expectOk(() => categoryApi.pageCategories({ page: 1, pageSize: 5, type: 'DISH' }))
    expect(page.records.every((item) => item.type === 'DISH')).toBe(true)
    expect(page.pageSize).toBe(5)

    await expectApiError(() => categoryApi.pageCategories({ page: 1, sort: 'password,desc' }), {
      status: 400,
      code: 'COMMON_SORT_FIELD_NOT_ALLOWED',
    })
  })
})

describe('菜品', () => {
  let categoryId = 0

  beforeAll(async () => {
    const category = await expectOk(() =>
      categoryApi.createCategory({ name: `集成菜品分类-${stamp}`, type: 'DISH', sortOrder: 0 }),
    )
    categoryId = category.id
    created.categories.push(category.id)
  })

  it('新增(带口味)→ 详情口味原样返回 → 编辑整体替换口味', async () => {
    const dish: DishDetail = await expectOk(() =>
      dishApi.createDish({
        categoryId,
        name: `集成菜品-${stamp}`,
        priceCents: 4800,
        sortOrder: 0,
        description: '集成测试用',
        flavors: [
          // 选项里故意带逗号:契约把选项存成字符串数组,不是逗号拼接
          { name: '辣度', options: ['不辣', '微辣', '中辣,特辣'], sortOrder: 0 },
          { name: '忌口', options: ['无', '不要香菜'], sortOrder: 1 },
        ],
      }),
    )
    created.dishes.push(dish.id)
    expect(dish.priceCents).toBe(4800)
    expect(dish.flavors).toHaveLength(2)
    expect(dish.flavors.find((flavor) => flavor.name === '辣度')?.options).toContain('中辣,特辣')

    const updated = await expectOk(() =>
      dishApi.updateDish(dish.id, {
        categoryId,
        name: `${dish.name}-改`,
        priceCents: 5200,
        status: 1,
        flavors: [{ name: '辣度', options: ['微辣'], sortOrder: 0 }],
      }),
    )
    expect(updated.priceCents).toBe(5200)
    expect(updated.flavors).toHaveLength(1)
    expect(updated.flavors[0].options).toEqual(['微辣'])
  })

  it('同分类重名 → 409 DISH_NAME_TAKEN;挂到套餐分类 → 422 DISH_CATEGORY_TYPE_MISMATCH', async () => {
    const name = `集成重名菜-${stamp}`
    const dish = await expectOk(() =>
      dishApi.createDish({ categoryId, name, priceCents: 1000, sortOrder: 0 }),
    )
    created.dishes.push(dish.id)

    await expectApiError(
      () => dishApi.createDish({ categoryId, name, priceCents: 1000, sortOrder: 0 }),
      { status: 409, code: 'DISH_NAME_TAKEN' },
    )

    const setmealCategory = await expectOk(() =>
      categoryApi.createCategory({ name: `集成套餐分类-${stamp}`, type: 'SETMEAL', sortOrder: 0 }),
    )
    created.categories.push(setmealCategory.id)

    await expectApiError(
      () => dishApi.createDish({ categoryId: setmealCategory.id, name: `${name}-x`, priceCents: 1000, sortOrder: 0 }),
      { status: 422, code: 'DISH_CATEGORY_TYPE_MISMATCH' },
    )
  })

  it('批量起售/停售与批量删除走同一组接口', async () => {
    const dish = await expectOk(() =>
      dishApi.createDish({ categoryId, name: `集成批量-${stamp}`, priceCents: 1500, sortOrder: 0, status: 1 }),
    )
    created.dishes.push(dish.id)

    await expectOk(() => dishApi.changeDishesStatus([dish.id], 0))
    expect((await expectOk(() => dishApi.getDish(dish.id))).status).toBe(0)

    await expectOk(() => dishApi.changeDishesStatus([dish.id], 1))
    expect((await expectOk(() => dishApi.getDish(dish.id))).status).toBe(1)

    await expectOk(() => dishApi.deleteDishes([dish.id]))
    created.dishes = created.dishes.filter((id) => id !== dish.id)
    await expectApiError(() => dishApi.getDish(dish.id), { status: 404, code: 'DISH_NOT_FOUND' })
  })

  it('删除不存在的菜品 → 404 DISH_NOT_FOUND', async () => {
    await expectApiError(() => dishApi.deleteDishes([999_999_999]), {
      status: 404,
      code: 'DISH_NOT_FOUND',
    })
  })
})

describe('套餐', () => {
  let setmealCategoryId = 0
  let dishId = 0
  let secondDishId = 0

  beforeAll(async () => {
    const category = await expectOk(() =>
      categoryApi.createCategory({ name: `集成套餐-${stamp}`, type: 'SETMEAL', sortOrder: 0 }),
    )
    setmealCategoryId = category.id
    created.categories.push(category.id)

    // 套餐需要真实菜品作组成;用菜品分类挂它们
    let dishCategoryId = created.categories.find((id) => id !== setmealCategoryId)
    if (!dishCategoryId) {
      const dishCategory = await expectOk(() =>
        categoryApi.createCategory({ name: `集成套餐菜品-${stamp}`, type: 'DISH', sortOrder: 0 }),
      )
      created.categories.push(dishCategory.id)
      dishCategoryId = dishCategory.id
    } else {
      // 借用一个"菜品分类"来放测试菜品
      const options = await expectOk(() => categoryApi.listCategoryOptions('DISH', true))
      dishCategoryId = options[0]?.id ?? dishCategoryId
    }

    const first = await expectOk(() =>
      dishApi.createDish({ categoryId: dishCategoryId, name: `集成套餐菜A-${stamp}`, priceCents: 3000, sortOrder: 0, status: 1 }),
    )
    const second = await expectOk(() =>
      dishApi.createDish({ categoryId: dishCategoryId, name: `集成套餐菜B-${stamp}`, priceCents: 2000, sortOrder: 0, status: 1 }),
    )
    dishId = first.id
    secondDishId = second.id
    created.dishes.push(first.id, second.id)
  })

  it('新增套餐:组成明细与联表字段都能读回', async () => {
    const setmeal: SetmealDetail = await expectOk(() =>
      setmealApi.createSetmeal({
        categoryId: setmealCategoryId,
        name: `集成套餐-${stamp}`,
        priceCents: 4500,
        items: [
          { dishId, copies: 1 },
          { dishId: secondDishId, copies: 2 },
        ],
      }),
    )
    created.setmeals.push(setmeal.id)
    expect(setmeal.items).toHaveLength(2)
    expect(setmeal.items.every((item) => typeof item.dishName === 'string')).toBe(true)

    const detail = await expectOk(() => setmealApi.getSetmeal(setmeal.id))
    expect(detail.items.map((item) => item.dishId).sort()).toEqual([dishId, secondDishId].sort())
  })

  it('空组成 → 422 SETMEAL_ITEMS_EMPTY;重复菜品 → 409 SETMEAL_ITEMS_DUPLICATED', async () => {
    await expectApiError(
      () =>
        setmealApi.createSetmeal({
          categoryId: setmealCategoryId,
          name: `集成空套餐-${stamp}`,
          priceCents: 1000,
          items: [],
        }),
      { status: 422, code: 'SETMEAL_ITEMS_EMPTY' },
    )

    await expectApiError(
      () =>
        setmealApi.createSetmeal({
          categoryId: setmealCategoryId,
          name: `集成重复套餐-${stamp}`,
          priceCents: 1000,
          items: [
            { dishId, copies: 1 },
            { dishId, copies: 1 },
          ],
        }),
      { status: 409, code: 'SETMEAL_ITEMS_DUPLICATED' },
    )
  })

  it('定价高于所含菜品合计 → 422 SETMEAL_PRICE_EXCEEDS_ITEMS', async () => {
    await expectApiError(
      () =>
        setmealApi.createSetmeal({
          categoryId: setmealCategoryId,
          name: `集成贵套餐-${stamp}`,
          priceCents: 999_999,
          items: [{ dishId, copies: 1 }],
        }),
      { status: 422, code: 'SETMEAL_PRICE_EXCEEDS_ITEMS' },
    )
  })

  it('被套餐引用的菜品不能删 → 422 SETMEAL_CONTAINS_DISH', async () => {
    const setmeal = await expectOk(() =>
      setmealApi.createSetmeal({
        categoryId: setmealCategoryId,
        name: `集成引用套餐-${stamp}`,
        priceCents: 2500,
        items: [{ dishId, copies: 1 }],
      }),
    )
    created.setmeals.push(setmeal.id)

    await expectApiError(() => dishApi.deleteDishes([dishId]), {
      status: 422,
      code: 'SETMEAL_CONTAINS_DISH',
    })
  })

  it('被菜品引用的分类不能删 → 422 CATEGORY_IN_USE', async () => {
    // 这个分类下一定有测试菜品(见 beforeAll)
    const dish = await expectOk(() => dishApi.getDish(dishId))
    await expectApiError(() => categoryApi.deleteCategory(dish.categoryId), {
      status: 422,
      code: 'CATEGORY_IN_USE',
    })
  })

  it('套餐也能批量启停用与删除', async () => {
    const setmeal = await expectOk(() =>
      setmealApi.createSetmeal({
        categoryId: setmealCategoryId,
        name: `集成批量套餐-${stamp}`,
        priceCents: 2000,
        // 注意:不传 status 时后端按停售(0)落库,起售要显式声明
        status: 1,
        items: [{ dishId: secondDishId, copies: 1 }],
      }),
    )
    expect(setmeal.status).toBe(1)

    await expectOk(() => setmealApi.changeSetmealsStatus([setmeal.id], 0))
    expect((await expectOk(() => setmealApi.getSetmeal(setmeal.id))).status).toBe(0)

    await expectOk(() => setmealApi.deleteSetmeals([setmeal.id]))
    await expectApiError(() => setmealApi.getSetmeal(setmeal.id), {
      status: 404,
      code: 'SETMEAL_NOT_FOUND',
    })
  })
})
