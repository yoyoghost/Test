package eh.msg.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.msg.ComLan;

import java.util.List;

public abstract class ComLanDAO extends HibernateSupportDelegateDAO<ComLan> {

    public ComLanDAO() {
        super();
        this.setEntityName(ComLan.class.getName());
        this.setKeyField("commonId");
    }

    @DAOMethod(limit = 10,orderBy = "LastModifyTime desc")
    public abstract List<ComLan> findByDoctorIdAndBussType(int doctorId,int bussType,long start);
}
