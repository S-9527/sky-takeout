package com.sky.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

import com.sky.catalog.domain.Dish;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 连带停售包含这些菜品的套餐(领域文档 §3.6:"停售任一菜品会连带停售相关套餐")。
     *
     * <p>一条 SQL 完成,避免"先查套餐 id 再逐条 update"的 N+1;必须在调用方的事务里执行。
     * 幂等:已经停售的套餐不会被重复更新。
     */
    @Update("""
            <script>
            UPDATE `setmeal` SET `status` = 0
            WHERE `status` = 1
              AND `id` IN (
                SELECT `setmeal_id` FROM `setmeal_item`
                WHERE `dish_id` IN
                <foreach collection="dishIds" item="dishId" open="(" separator="," close=")">#{dishId}</foreach>
              )
            </script>
            """)
    int disableSetmealsContaining(@Param("dishIds") Collection<Long> dishIds);
}
