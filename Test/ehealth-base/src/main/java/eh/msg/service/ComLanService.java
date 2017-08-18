package eh.msg.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.entity.msg.ComLan;
import eh.msg.dao.ComLanDAO;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

public class ComLanService {


    /**
     * 新增常用语
     *
     * @param comLan
     * @return Boolean
     * @date 2016-5-20
     * @author luf
     */
    @RpcService
    public Boolean addCommonLan(ComLan comLan) {
        if (comLan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "comLan is required!");
        }
        if (comLan.getDoctorId() == null || comLan.getDoctorId() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        if (comLan.getBussType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bussType is required!");
        }
        if (StringUtils.isEmpty(comLan.getWords())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "words is required!");
        }
        ComLanDAO comLanDAO = DAOFactory.getDAO(ComLanDAO.class);
        Date now = new Date();
        comLan.setCommonId(null);
        comLan.setCreateTime(now);
        comLan.setLastModifyTime(now);
        comLan.setBussType(0);
        comLanDAO.save(comLan);
        return true;
    }

    /**
     * 更新聊天常用语
     *
     * @param comLan
     * @return Boolean
     * @date 2016-5-20
     * @author luf
     */
    @RpcService
    public Boolean updateCommonLan(ComLan comLan) {
        if (comLan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "comLan is required!");
        }
        if (comLan.getCommonId() == null || comLan.getCommonId() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "commonId is required!");
        }
        if (StringUtils.isEmpty(comLan.getWords())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "words is required!");
        }
        ComLanDAO comLanDAO = DAOFactory.getDAO(ComLanDAO.class);
        ComLan cl = comLanDAO.get(comLan.getCommonId());
        if (cl == null) {
            return false;
        }
        comLan.setDoctorId(null);
        comLan.setBussType(null);
        comLan.setCreateTime(null);
        comLan.setLastModifyTime(new Date());
        BeanUtils.map(comLan, cl);
        comLanDAO.update(cl);
        return true;
    }

    /**
     * 删除常用语
     *
     * @param comLan
     * @return Boolean
     * @date 2016-5-20
     * @author luf
     */
    @RpcService
    public Boolean deleteCommonLan(ComLan comLan) {
        if (comLan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "comLan is required!");
        }
        if (comLan.getCommonId() == null || comLan.getCommonId() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "commonId is required!");
        }
        ComLanDAO comLanDAO = DAOFactory.getDAO(ComLanDAO.class);
        Integer comId = comLan.getCommonId();
        ComLan cl = comLanDAO.get(comId);
        if (cl == null) {
            return false;
        }
        comLanDAO.remove(comId);
        return true;
    }

    /**
     * 获取所有常用语
     *
     * @param doctorId
     * @param bussType 业务类型-0全部
     * @param start    页面开始位置
     * @return List<ComLan>
     * @date 2016-5-20
     * @author luf
     */
    @RpcService
    public List<ComLan> findAllCommonsBuss(int doctorId, int bussType, long start) {
        ComLanDAO comLanDAO = DAOFactory.getDAO(ComLanDAO.class);
        return comLanDAO.findByDoctorIdAndBussType(doctorId, 0, start);//bussType=0,查全部
    }
}
