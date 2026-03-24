package com.mobigen.aiop.nttpoc.module.menu.dao;

import java.util.List;
import java.util.Map;

public interface MenuDao {
    List<Map<String, Object>> selectMenuByUserSeq(long userSeq);
}
