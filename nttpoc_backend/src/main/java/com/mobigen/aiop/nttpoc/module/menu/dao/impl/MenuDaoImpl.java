package com.mobigen.aiop.nttpoc.module.menu.dao.impl;

import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.mobigen.aiop.nttpoc.module.menu.dao.MenuDao;

@Repository
public class MenuDaoImpl implements MenuDao {

    private final SqlSessionTemplate sqlSessionTemplateNttpocDb1;
    private final String namespace = "com.mobigen.aiop.nttpoc.module.menu";

    @Autowired
    public MenuDaoImpl(SqlSessionTemplate sqlSessionTemplateNttpocDb1) {
        this.sqlSessionTemplateNttpocDb1 = sqlSessionTemplateNttpocDb1;
    }

    @Override
    public List<Map<String, Object>> selectMenuByUserSeq(long userSeq) {
        return sqlSessionTemplateNttpocDb1.selectList(namespace + ".selectMenuByUserSeq", userSeq);
    }
}
