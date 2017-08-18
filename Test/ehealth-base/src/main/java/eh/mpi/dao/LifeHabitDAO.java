package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.LifeHabit;
import org.apache.log4j.Logger;

/**
 * Created by Administrator on 2017-04-14.
 */
public abstract class LifeHabitDAO extends HibernateSupportDelegateDAO<LifeHabit> {
    public static final Logger log = Logger.getLogger(HealthCardDAO.class);

    public LifeHabitDAO() {
        super();
        this.setEntityName(LifeHabit.class.getName());
        this.setKeyField("lifeHabitId");
    }


    @Override
    public boolean exist(Object id) throws DAOException {
        return super.exist(id);
    }

    @Override
    public LifeHabit update(LifeHabit o) throws DAOException {
        return super.update(o);
    }
    @RpcService
    @DAOMethod(sql = "from LifeHabit where mpiId=:mpiId")
    public abstract LifeHabit getLifeHabitByMpiId(@DAOParam("mpiId")String mpiId);

    public LifeHabit addLifeHabit(LifeHabit lifeHabit){
        return save(lifeHabit);
    }
}
