package eh.bus.dao;

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.BusMoneyDistributeRecord;

import java.util.Date;


public class BusMoneyDistributeRecordDAO
        extends HibernateSupportDelegateDAO<BusMoneyDistributeRecord> {
    public BusMoneyDistributeRecordDAO() {
        super();
        setEntityName(BusMoneyDistributeRecord.class.getName());
        setKeyField("Id");
    }

    @Override
    public BusMoneyDistributeRecord save(BusMoneyDistributeRecord record) {
        record.setCreateDate(record.getCreateDate()==null?new Date():record.getCreateDate());
        return super.save(record);
    }
}
