package com.ruiding.erp.dal.mysql.masterdata;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.masterdata.RdCustomerProductDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdCustomerProductMapper extends BaseMapperX<RdCustomerProductDO> {

    @Insert("INSERT INTO rd_customer_product "
            + "(tenant_id, customer_name, product_model, source_type, last_used_time, status, "
            + "creator, create_time, updater, update_time, deleted) "
            + "VALUES (#{tenantId}, #{customerName}, #{productModel}, 'BUSINESS', NOW(), 0, "
            + "'system', NOW(), 'system', NOW(), b'0') "
            + "ON DUPLICATE KEY UPDATE last_used_time = VALUES(last_used_time), "
            + "status = 0, updater = 'system', update_time = NOW()")
    int upsertPair(@Param("tenantId") Long tenantId,
                   @Param("customerName") String customerName,
                   @Param("productModel") String productModel);

    @Select({"<script>",
            "SELECT DISTINCT customer_name FROM rd_customer_product",
            "WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND customer_name LIKE CONCAT('%', #{keyword}, '%')",
            "</if>",
            "ORDER BY customer_name LIMIT #{limit}",
            "</script>"})
    List<String> selectCustomerOptions(@Param("tenantId") Long tenantId,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit);

    @Select({"<script>",
            "SELECT DISTINCT product_model FROM rd_customer_product",
            "WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 0",
            "<if test='customerName != null and customerName != \"\"'>",
            "AND customer_name = #{customerName}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND product_model LIKE CONCAT('%', #{keyword}, '%')",
            "</if>",
            "ORDER BY product_model LIMIT #{limit}",
            "</script>"})
    List<String> selectModelOptions(@Param("tenantId") Long tenantId,
                                    @Param("customerName") String customerName,
                                    @Param("keyword") String keyword,
                                    @Param("limit") int limit);
}
