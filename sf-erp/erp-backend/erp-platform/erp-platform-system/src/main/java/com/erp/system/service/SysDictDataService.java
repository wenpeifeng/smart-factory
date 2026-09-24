package com.erp.system.service;

import com.erp.common.result.PageResult;
import com.erp.system.dto.dict.DictDataForm;
import com.erp.system.dto.dict.DictDataQuery;
import com.erp.system.dto.dict.DictDataVO;
import com.erp.system.dto.dict.DictItemVO;

import java.util.List;

/**
 * 字典数据管理服务。
 */
public interface SysDictDataService {

    /**
     * 分页查询字典项（按类型过滤）。
     *
     * @param query 查询条件
     * @return 字典项 VO 分页
     */
    PageResult<DictDataVO> page(DictDataQuery query);

    /**
     * 新增字典项（同类型下 label/value 唯一）。
     *
     * @param form 字典项表单
     * @return 新字典项 ID
     */
    Long create(DictDataForm form);

    /**
     * 修改字典项。
     *
     * @param form 字典项表单（含 id）
     */
    void update(DictDataForm form);

    /**
     * 删除字典项（逻辑删除）。
     *
     * @param id 字典项 ID
     */
    void delete(Long id);

    /**
     * 按类型编码取启用的字典选项（公开接口 /dict/{type} 的数据源）。
     *
     * @param type 类型编码
     * @return label/value 选项列表（按 sort 升序），类型不存在或停用时返回空列表
     */
    List<DictItemVO> listEnabledByType(String type);
}
