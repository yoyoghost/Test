package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.ActionHabit;
import org.apache.log4j.Logger;

/**
 * Created by renzk on 2017/6/15.
 */
public abstract class ActionHabitDAO extends HibernateSupportDelegateDAO<ActionHabit> {
    public static final Logger log = Logger.getLogger(ActionHabitDAO.class);

    public ActionHabitDAO() {
        super();
        this.setEntityName(ActionHabit.class.getName());
        this.setKeyField("actionHabitId");
    }


    @Override
    public boolean exist(Object id) throws DAOException {
        return super.exist(id);
    }

    @Override
    public ActionHabit update(ActionHabit o) throws DAOException {
        return super.update(o);
    }
    @RpcService
    @DAOMethod(sql = "from ActionHabit where mpiId=:mpiId")
    public abstract ActionHabit getActionHabitByMpiId(@DAOParam("mpiId")String mpiId);

    public ActionHabit addActionHabit(ActionHabit actionHabit){
        return save(actionHabit);
    }
}
