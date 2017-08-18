package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.base.dao.DrugListDAO;
import eh.base.dao.OrganDrugListDAO;
import eh.base.dao.SaleDrugListDAO;
import eh.cdr.dao.DrugsEnterpriseDAO;
import eh.entity.base.DrugList;
import eh.entity.base.OrganDrugList;
import eh.entity.base.SaleDrugList;
import eh.entity.cdr.DrugsEnterprise;
import eh.util.HttpHelper;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * 药企数据准备类，前端不进行调用
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/7/18.
 */
public class DrugsEnterpriseTestService {
    public static final Logger logger = Logger.getLogger(DrugsEnterpriseTestService.class);

    public void canToSend(int drugId, int organId){
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        DrugList drugList = drugListDAO.getById(drugId);
        if(null == drugList){
            logger.info("药品["+drugId+"]没有");
            return;
        }

        OrganDrugList organDrugList = organDrugListDAO.getByDrugIdAndOrganId(drugId,organId);
        if(null == organDrugList){
            logger.info("药品["+drugId+"]医院["+organId+"]不能开");
            return;
        }

        SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganId(drugId,organId);
        if(null == saleDrugList){
            logger.info("药品["+drugId+"]医院["+organId+"]药企不能配送");
            return;
        }
        logger.info("药品["+drugId+"]医院["+organId+"]通过验证");
    }

    /**
     * 将数据库中药企可配送的所有药品推送到药企
     */
    public void pushToEnterpriseDrugs(Integer depId){
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);

        List<DrugList> drugList = drugListDAO.findAll();
        logger.info("drug length:"+drugList.size());

        List<Integer> hfDrugIds = saleDrugListDAO.findDrugIdByOrganId(depId);
        logger.info("sale drug:"+hfDrugIds.size());

        List<Map<String, Object>> drugsList = new ArrayList<>();
        Map<String, Object> drugMap;

        //处理药品数据
        for(DrugList drug : drugList){
            if(null != drug && hfDrugIds.contains(drug.getDrugId())){
                drugMap = new HashMap<>();
                drugMap.put("producer", drug.getProducer());
                drugMap.put("gname", drug.getDrugName());
                drugMap.put("goodsid", drug.getDrugId());
                drugMap.put("msunitno", drug.getUnit());
                drugMap.put("spec", drug.getDrugSpec());
                drugMap.put("drugname", drug.getSaleName());
                //件包装
//                        drugMap.put("packnum", 1);
                //产地
//                        drugMap.put("prdarea", "");

                drugsList.add(drugMap);
            }
        }

        logger.info("fitle drug:"+drugsList.size());


        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise de = drugsEnterpriseDAO.getById(depId);

        Map<String, Object> sendMap = new HashMap<>();
        sendMap.put("access_token", de.getToken());
        sendMap.put("action", "setGoods");


        //分批推送
        int yz = 50;
        int count = (int)Math.ceil(drugsList.size()/50.0);
        List<Map<String, Object>> everyCountList = new ArrayList<>();
        for(int i=0;i<count;i++){
            everyCountList.clear();
            int start = i*yz;
            int end = (i+1)*yz;
            if(end > drugsList.size()){
                end = drugsList.size();
            }
            logger.info("start:"+start+"***end:"+end);
            for(int k=start;k<end;k++){
                everyCountList.add(drugsList.get(k));
            }
            logger.info("everyCountList size:"+everyCountList.size());
            if(!everyCountList.isEmpty()) {
                sendMap.put("data", everyCountList);

                String backMsg = "";
                try {
                    backMsg = HttpHelper.doPost(de.getBusinessUrl(), JSONUtils.toString(sendMap));
                    logger.info("药企返回信息: "+backMsg);
                } catch (Exception e) {
                    logger.info("IOException "+e.getMessage()+"，详细数据："+JSONUtils.toString(sendMap));
                    backMsg = "";
                }

                if(StringUtils.isNotEmpty(backMsg)){
                    // code 1成功
                    Map backMap = JSONUtils.parse(backMsg,Map.class);
                    if(1 == MapValueUtil.getInteger(backMap,"code")){
                        logger.info("药品推送成功:"+backMsg);
                    }else{
                        logger.info("失败信息："+MapValueUtil.getString(backMap,"message")+"***推送信息:"+JSONUtils.toString(sendMap));
                    }
                }
            }
        }
    }


    /**
     * 分析药品数据，哪些需要加，哪些需要改
     *
     * @return
     */
    public Map<String, Object> analysisDrugList(List<Integer> drugIdList, int organId, boolean useFile) throws IOException{
        String filePth = "/home/cdr_update_1.sql";
        if(System.getProperty("os.name").toLowerCase().contains("windows")){
            filePth = "d:/cdr_update_1.sql";
        }
        FileWriter fw = null;
        if(useFile) {
            fw = new FileWriter(filePth, false);
        }
        String lineSign = System.getProperty ("line.separator");
        String formatTime = DateConversion.getDateFormatter(new Date(),DateConversion.YYYY_MM_DD);
        formatTime += " 00:00:00";

        Map<String, Object> reMap = new LinkedHashMap<>();

        if(CollectionUtils.isNotEmpty(drugIdList)){
            //分析base_saledruglist
            SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
            DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);

            SaleDrugList saleDrug;
            List<Integer> saleUpdateId = new ArrayList<>(10);
            List<Integer> saleAddId = new ArrayList<>(10);
            for (Integer drugId : drugIdList){
                saleDrug = saleDrugListDAO.getByDrugIdAndOrganId(drugId,organId);
                if(null != saleDrug){
                    if(!Integer.valueOf(1).equals(saleDrug.getStatus())){
                        //需要修改成status=1
                        saleUpdateId.add(drugId);
                    }
                }else {
                    saleAddId.add(drugId);
                }
            }

            if(CollectionUtils.isNotEmpty(saleAddId)){
                if(useFile) {
                    fw.write("sale_AddId  size=" + saleAddId.size() + " , detail=" + JSONUtils.toString(saleAddId) + lineSign);
                }else{
                    reMap.put("sale_AddId", "size="+saleAddId.size()+" , detail="+JSONUtils.toString(saleAddId));
                }
                StringBuilder addSql = new StringBuilder();
                List<Integer> notExistDrug = new ArrayList<>(10);
                DrugList drug;
                for(Integer drugId : saleAddId){
                    drug = drugListDAO.get(drugId);
                    if(null != drug) {
                        addSql.append("INSERT INTO base_saledruglist(OrganID,DrugId,OrganDrugCode,Price,Rate,RatePrice,Status) " +
                                "VALUES ( " + organId + ", " + drugId + ", '', "+drug.getPrice1()+", NULL, NULL, 1);"+lineSign);
                    }else{
                        notExistDrug.add(drugId);
                    }
                }

                if(useFile){
                    fw.write("sale_NotExistDrug : "+JSONUtils.toString(notExistDrug)+lineSign);
                    fw.write("sale_AddSql : "+lineSign+addSql.toString()+lineSign);
                }else {
                    reMap.put("sale_NotExistDrug",JSONUtils.toString(notExistDrug));
                    reMap.put("sale_AddSql",addSql.toString());
                }
            }

            if(CollectionUtils.isNotEmpty(saleUpdateId)){
                if(useFile) {
                    fw.write("sale_UpdateId  size=" + saleUpdateId.size() + " , detail=" + JSONUtils.toString(saleUpdateId) + lineSign);
                }else{
                    reMap.put("sale_UpdateId","size="+saleUpdateId.size()+" , detail="+JSONUtils.toString(saleUpdateId));
                }
                StringBuilder updateSql = new StringBuilder();
                updateSql.append("update base_saledruglist set Status=1 where OrganID="+organId+" and DrugId in ("+StringUtils.join(saleUpdateId,",")+");");

                if(useFile) {
                    fw.write("sale_UpdateSql : " + lineSign + updateSql.toString() + lineSign);
                }else{
                    reMap.put("sale_UpdateSql",updateSql.toString());
                }
            }

            if(useFile) {
                fw.write(lineSign + lineSign + lineSign);
            }

            //分析base_organdruglist
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            OrganDrugList organDrug;
            List<Integer> organUpdateId = new ArrayList<>(10);
            //可能是带*的一些药品
            List<Integer> organUpdateNotFilterId = new ArrayList<>(10);
            List<Integer> organAddId = new ArrayList<>(10);
            DrugList drug;
            for (Integer drugId : drugIdList){
                organDrug = organDrugListDAO.getByDrugIdAndOrganId(drugId,organId);
                if(null != organDrug){
                    if(!Integer.valueOf(1).equals(organDrug.getStatus())){
                        drug = drugListDAO.get(drugId);
                        if(null != drug) {
                            boolean use = isOrganCanUseDrug(drug.getDrugName());
                            if(use) {
                                organUpdateId.add(drugId);
                            }else{
                                organUpdateNotFilterId.add(drugId);
                            }
                        }
                    }
                }else {
                    organAddId.add(drugId);
                }
            }

            if(CollectionUtils.isNotEmpty(organAddId)){
                if(useFile) {
                    fw.write("organ_AddId  size=" + organAddId.size() + " , detail=" + JSONUtils.toString(organAddId) + lineSign);
                }else{
                    reMap.put("organ_AddId","size="+organAddId.size()+" , detail="+JSONUtils.toString(organAddId));
                }
                StringBuilder addSql = new StringBuilder();
                List<Integer> notExistDrug = new ArrayList<>(10);
                for(Integer drugId : organAddId){
                    drug = drugListDAO.get(drugId);
                    if(null != drug) {
                        boolean use = isOrganCanUseDrug(drug.getDrugName());

                        addSql.append("INSERT INTO base_organdruglist(OrganID,DrugId,OrganDrugCode,salePrice,ProducerCode,Status) " +
                                "VALUES (" + organId + ", " + drugId + ", '', "+drug.getPrice1()+", '', "+(use?1:0)+");"+lineSign);
                    }else{
                        notExistDrug.add(drugId);
                    }
                }

                if(useFile) {
                    fw.write("organ_NotExistDrug : " + JSONUtils.toString(notExistDrug) + lineSign);
                    fw.write("organ_AddSql : " + lineSign + addSql.toString() + lineSign);
                }else{
                    reMap.put("organ_NotExistDrug",JSONUtils.toString(notExistDrug));
                    reMap.put("organ_AddSql",addSql.toString());
                }
            }

            if(CollectionUtils.isNotEmpty(organUpdateId)){
                if(useFile) {
                    fw.write("organ_notFilterId  size=" + organUpdateNotFilterId.size() + " , detail=" + JSONUtils.toString(organUpdateNotFilterId) + lineSign);
                    fw.write("organ_UpdateId  size="+organUpdateId.size()+" , detail="+JSONUtils.toString(organUpdateId)+lineSign);
                }else{
                    reMap.put("organ_notFilterId","size="+organUpdateNotFilterId.size()+" , detail="+JSONUtils.toString(organUpdateNotFilterId));
                    reMap.put("organ_UpdateId","size="+organUpdateId.size()+" , detail="+JSONUtils.toString(organUpdateId));
                }

                StringBuilder updateSql = new StringBuilder();
                updateSql.append("update base_organdruglist set Status=1 where OrganID="+organId+" and DrugId in ("+StringUtils.join(organUpdateId,",")+");");

                if(useFile) {
                    fw.write("organ_UpdateSql : " + lineSign + updateSql.toString() + lineSign);
                }else {
                    reMap.put("organ_UpdateSql",updateSql.toString());
                }
            }

        }

        if(useFile) {
            fw.flush();
            fw.close();
            reMap.put("info", "see file path:" + filePth);
        }

        return reMap;
    }

    /**
     * base_organdruglist不能开具的药品规则
     * @param drugName
     * @return
     */
    private boolean isOrganCanUseDrug(String drugName){
        boolean bl = true;

        if(StringUtils.isEmpty(drugName)){
            bl = false;
        }

        if(bl){
            if(drugName.startsWith("*") || drugName.contains("赠药")) {
                bl = false;
            }
        }

        return bl;
    }
}
