package com.ruiding.erp.dal.mysql.production;

import com.ruiding.erp.controller.admin.production.vo.RdProductionLayerOptionRespVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RdProductionChoiceMapper {
    String HISTORY = "FROM (SELECT furnace_no,furnace_layer,customer_name,product_model,production_date AS biz_date "
            + "FROM rd_production_output WHERE tenant_id=#{tenantId} AND deleted=0 AND status != 'CANCELLED' "
            + "UNION ALL SELECT furnace_no,furnace_layer,customer_name,product_model,last_biz_date AS biz_date "
            + "FROM rd_stock_lot WHERE tenant_id=#{tenantId} AND deleted=0) history ";
    String SCOPE = "<if test='customerName != null and customerName != \"\"'>AND customer_name=#{customerName}</if> "
            + "<if test='productModel != null and productModel != \"\"'>AND product_model=#{productModel}</if> ";

    @Select({"<script>SELECT MIN(furnace_no) AS furnace_no ", HISTORY,
            "WHERE furnace_no IS NOT NULL AND TRIM(furnace_no) != '' ", SCOPE,
            "<if test='keyword != null and keyword != \"\"'>AND furnace_no LIKE CONCAT('%',#{keyword},'%')</if> ",
            "GROUP BY CAST(furnace_no AS BINARY) ORDER BY MAX(biz_date) DESC,CAST(MIN(furnace_no) AS BINARY) LIMIT #{limit}</script>"})
    List<String> selectFurnaces(@Param("tenantId") Long tenantId, @Param("customerName") String customerName,
                               @Param("productModel") String productModel, @Param("keyword") String keyword,
                               @Param("limit") int limit);

    @Select({"<script>SELECT layer_name AS value,CONCAT(layer_name,' (',layer_code,')') AS label,'DICTIONARY' AS source ",
            "FROM rd_furnace_layer WHERE tenant_id=#{tenantId} AND deleted=0 AND status=0 AND TRIM(layer_name) != '' ",
            "<if test='keyword != null and keyword != \"\"'>AND (layer_name LIKE CONCAT('%',#{keyword},'%') OR layer_code LIKE CONCAT('%',#{keyword},'%'))</if> ",
            "ORDER BY sort,id LIMIT #{limit}</script>"})
    List<RdProductionLayerOptionRespVO> selectEnabledLayers(@Param("tenantId") Long tenantId,
                                                          @Param("keyword") String keyword, @Param("limit") int limit);

    @Select({"<script>SELECT MIN(furnace_layer) AS value,MIN(furnace_layer) AS label,'HISTORY' AS source ", HISTORY,
            "WHERE furnace_layer IS NOT NULL AND TRIM(furnace_layer) != '' ", SCOPE,
            "AND NOT EXISTS (SELECT 1 FROM rd_furnace_layer d WHERE d.tenant_id=#{tenantId} AND d.deleted=0 AND d.status != 0 ",
            "AND (CAST(d.layer_name AS BINARY)=CAST(history.furnace_layer AS BINARY) OR CAST(d.layer_code AS BINARY)=CAST(history.furnace_layer AS BINARY))) ",
            "<if test='keyword != null and keyword != \"\"'>AND furnace_layer LIKE CONCAT('%',#{keyword},'%')</if> ",
            "GROUP BY CAST(furnace_layer AS BINARY) ORDER BY MAX(biz_date) DESC,CAST(MIN(furnace_layer) AS BINARY) LIMIT #{limit}</script>"})
    List<RdProductionLayerOptionRespVO> selectHistoryLayers(@Param("tenantId") Long tenantId,
                                                         @Param("customerName") String customerName,
                                                         @Param("productModel") String productModel,
                                                         @Param("keyword") String keyword, @Param("limit") int limit);
}
