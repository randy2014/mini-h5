package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer sort;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
