package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DrugListDAO;
import eh.base.dao.DrugProducerDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.OrganDrugListDAO;
import eh.entity.base.DrugList;
import eh.entity.base.DrugProducer;
import eh.entity.base.OrganDrugList;
import eh.entity.bus.DrugListAndOrganDrugList;
import eh.op.auth.service.SecurityService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by zhongzx on 2016/5/27 0027.
 * 机构药品服务
 */
public class OrganDrugListService {

    private static Logger logger = Logger.getLogger(OrganDrugListService.class);


    /**
     * 把药品添加到对应医院
     *
     * @param organDrugList
     * @return
     * @author zhongzx
     */
    @RpcService
    public boolean addDrugListForOrgan(OrganDrugList organDrugList) {
        OrganDrugListDAO dao = DAOFactory.getDAO(OrganDrugListDAO.class);
        logger.info("新增机构药品服务[addDrugListForOrgan]:" + JSONUtils.toString(organDrugList));
        if (null == organDrugList) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organDrugList is null");
        }
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (!organDAO.exist(organDrugList.getOrganId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Organ not exist");
        }
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        if (!drugListDAO.exist(organDrugList.getDrugId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "DrugList not exist");
        }

        //验证药品必要信息
        validate(organDrugList);
        dao.save(organDrugList);
        return true;
    }

    private void validate(OrganDrugList organDrugList) {
        if (null == organDrugList) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品信息不能为空");
        }
        if (StringUtils.isEmpty(organDrugList.getOrganDrugCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organDrugCode is needed");
        }
        /*if (StringUtils.isEmpty(organDrugList.getProducerCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "producerCode is needed");
        }*/
        if (null == organDrugList.getDrugId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is needed");
        }
        if (null == organDrugList.getOrganId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is needed");
        }
        if (null == organDrugList.getSalePrice()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "salePrice is needed");
        }
        organDrugList.setCreateDt(new Date());
        if (null == organDrugList.getStatus()) {
            organDrugList.setStatus(1);
        }
        organDrugList.setLastModify(new Date());
    }

    private void validateOrganDrugList(OrganDrugList organDrugList) {
        if (null == organDrugList) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品信息不能为空");
        }
        if (StringUtils.isEmpty(organDrugList.getOrganDrugCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organDrugCode is needed");
        }
        /*if (StringUtils.isEmpty(organDrugList.getProducerCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "producerCode is needed");
        }*/
        if (null == organDrugList.getDrugId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is needed");
        }
        if (null == organDrugList.getOrganId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is needed");
        }
        if (null == organDrugList.getSalePrice()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "salePrice is needed");
        }
    }

    /**
     * 批量导入邵逸夫药品（暂时用）
     *
     * @return
     * @author zhongzx
     */
    public void addDrugListForBatch() {
        DrugListDAO dao = DAOFactory.getDAO(DrugListDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        DrugProducerDAO producerDAO = DAOFactory.getDAO(DrugProducerDAO.class);
        List<DrugList> list = dao.findAll();
        for (DrugList d : list) {
            OrganDrugList organDrug = new OrganDrugList();
            organDrug.setDrugId(d.getDrugId());
            organDrug.setOrganId(1);
            organDrug.setCreateDt(new Date());
            organDrug.setLastModify(new Date());
            //把药品产地转换成相应的医院的代码
            List<DrugProducer> producers = producerDAO.findByNameAndOrgan(d.getProducer(), 1);
            if (null != producers && producers.size() > 0) {
                organDrug.setProducerCode(producers.get(0).getCode());
            } else {
                organDrug.setProducerCode("");
            }
            organDrug.setStatus(1);
            organDrugListDAO.save(organDrug);
        }
    }

    /**
     * 更新药品在医院中的信息
     *
     * @param organDrugList
     * @return
     * @author zhongzx
     */
    @RpcService
    public OrganDrugList updateOrganDrugList(OrganDrugList organDrugList) {
        logger.info("修改机构药品服务[updateOrganDrugList]:" + JSONUtils.toString(organDrugList));
        if (null == organDrugList.getDrugId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is required");
        }
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        OrganDrugList target = organDrugListDAO.get(organDrugList.getOrganDrugId());
        if (null == target) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "此药在该医院药品列表中不存在");
        } else {
            BeanUtils.map(organDrugList, target);
            target.setLastModify(new Date());
            validateOrganDrugList(target);
            target = organDrugListDAO.update(target);
        }
        return target;
    }

    /**
     * 机构药品查询
     *
     * @param organId   机构
     * @param drugClass 药品分类
     * @param keyword   查询关键字:药品序号 or 药品名称 or 生产厂家 or 商品名称 or 批准文号
     * @param start
     * @param limit
     * @return
     * @author houxr
     */
    @RpcService
    public QueryResult<DrugListAndOrganDrugList> queryOrganDrugListByOrganIdAndKeyword(final Integer organId,
                                                                                       final String drugClass,
                                                                                       final String keyword, final Integer status,
                                                                                       final int start, final int limit) {
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        return organDrugListDAO.queryOrganDrugListByOrganIdAndKeyword(organId, drugClass, keyword, status, start, limit);
    }

    /**
     *
     * * 运营平台（权限改造）
     *
     * @param organId
     * @param drugClass
     * @param keyword
     * @param status
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<DrugListAndOrganDrugList> queryOrganDrugListByOrganIdAndKeywordForOp(final Integer organId,
                                                                                       final String drugClass,
                                                                                       final String keyword, final Integer status,
                                                                                       final int start, final int limit) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organId);
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        return organDrugListDAO.queryOrganDrugListByOrganIdAndKeyword(organId, drugClass, keyword, status, start, limit);
    }

}
