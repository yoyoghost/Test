package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.information.InfoCollection;

import java.util.List;

/**
 * Created by zhangyq on 2017/2/15.
 */
public abstract class InfoCollectionDAO extends HibernateSupportDelegateDAO<InfoCollection>{
    public InfoCollectionDAO(){
        super();
        this.setEntityName(InfoCollection.class.getName());
        this.setKeyField("InfoCollectionId");
    }

    @DAOMethod(sql = "from InfoCollection  where doctorId=:doctorId")
    public abstract List<InfoCollection> findInformationByDoctorId(@DAOParam("doctorId")Integer doctorId,
                                                                     @DAOParam(pageStart = true) int start,
                                                                     @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "delete from InfoCollection where doctorId=:doctorId and informationId=:informationId")
    public abstract void deleteByDoctorIdAndInformationId(@DAOParam("doctorId") Integer doctorId,@DAOParam("informationId") Integer informationId);

    @DAOMethod
    public abstract InfoCollection getByDoctorIdAndInformationId(Integer doctorId,Integer informationId);

    @DAOMethod(sql = "delete from InfoCollection where informationId=:informationId")
    public abstract void  deleteByInformationId(@DAOParam("informationId") Integer informationId);
}
