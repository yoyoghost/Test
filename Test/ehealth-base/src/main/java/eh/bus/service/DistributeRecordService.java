package eh.bus.service;


import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.bus.dao.BusMoneyDistributeRecordDAO;
import eh.entity.bus.BusMoneyDistributeRecord;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;


public class DistributeRecordService {
    private static final Logger logger = Logger.getLogger(DistributeRecordService.class);

   public List<BusMoneyDistributeRecord> saveRecordList(final List<BusMoneyDistributeRecord> list){
       HibernateStatelessResultAction<List<BusMoneyDistributeRecord>> action =
               new AbstractHibernateStatelessResultAction<List<BusMoneyDistributeRecord>>() {
           @Override
           public void execute(StatelessSession ss) throws Exception {
               BusMoneyDistributeRecordDAO dao=DAOFactory.getDAO(BusMoneyDistributeRecordDAO.class);

               List<BusMoneyDistributeRecord> savedList=new ArrayList<>();
               for (BusMoneyDistributeRecord record:list){
                   savedList.add( dao.save(record) );
               }

               setResult(savedList);
           }
       };

       HibernateSessionTemplate.instance().execute(action);
      return  action.getResult();
   }

}
