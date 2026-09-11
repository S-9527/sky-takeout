package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import com.sky.common.persistence.AuditableEntity;
import com.sky.common.util.Json;

/**
 * 菜品口味维度(D7:选项存 JSON 数组,不存逗号拼接字符串)。
 *
 * <p>列 {@code options} 是 MySQL {@code JSON NOT NULL}。这里刻意把字段存成字符串、
 * 由 {@link #getOptions()} / {@link #setOptions(List)} 转成 {@code List<String>},
 * 而不是挂一个 JSON TypeHandler:
 * <ul>
 *   <li>MyBatis-Plus 自带的 Jackson TypeHandler 编译在 Jackson 2 上,而 Boot 4 的容器用 Jackson 3
 *       ——挂上去等于在项目里再养一套 JSON 配置;</li>
 *   <li>TypeHandler 必须被实体类引用,会把 {@code domain} 包绑到 ORM 扩展包上。</li>
 * </ul>
 * 现在领域模型拿到的是强类型列表,"逗号损坏"这个旧问题在类型上就不可能出现。
 */
@Getter
@Setter
@TableName("dish_flavor")
public class DishFlavor extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    /** 口味维度名,如"辣度""忌口";同一菜品内唯一。 */
    private String name;

    /** 数据库列 {@code options},内容是 JSON 字符串数组。外部只通过 getOptions/setOptions 访问。 */
    @TableField("options")
    private String optionsJson;

    private Integer sortOrder;

    public List<String> getOptions() {
        return Json.readStringList(optionsJson);
    }

    public void setOptions(List<String> options) {
        this.optionsJson = Json.write(options);
    }
}
