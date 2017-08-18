package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.DiseasDAO;
import eh.entity.base.Diseas;

import java.util.List;

/**
 * Created by zhongzx on 2016/4/27 0027.
 */
public class DiseasService {

    /**
     * 查询医生对应机构 常用诊断 10条
     * zhongzx
     * @param doctor
     * @param organId
     * @return
     */
    @RpcService
    public List<Diseas> findCommonDiseasByDoctorAndOrganId(int doctor, int organId){
        DiseasDAO diseasDAO = DAOFactory.getDAO(DiseasDAO.class);
        return diseasDAO.findCommonDiseasByDoctorAndOrganId(doctor, organId, 0, 10);
    }

    /**
     * 根据名字或者首字母搜索疾病诊断
     * zhongzx
     * @param organId
     * @param name
     * @return
     */
    @RpcService
    public List<Diseas> findDiseasByNameOrCode(int organId,String name,int start,int limit){
        DiseasDAO diseasDAO = DAOFactory.getDAO(DiseasDAO.class);
        return diseasDAO.findByDiseasNameLikeNew(organId,name,start,limit);
    }
}
