package eh.cdr.service;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.util.PropertyUtil;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wnw on 2016/11/3.
 */
public class ClinicEmrService {
    /**
     * 中医馆获取医院电子病历访问地址
     * @param mpiid
     * @return
     */
    @RpcService
    public Map<String,Object> getEmrUrlByMpiId(String mpiid) throws DAOException {
        Map<String,Object> res=new HashMap<>();
        res.put("showEmrButton",false);//是否显示
        res.put("emrUrl","");//电子病历调用地址
        if(StringUtils.isEmpty(mpiid)){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"mpiid can not be null or empty");
        }

        String patientId=getPatientId(mpiid);
        if(StringUtils.isEmpty(patientId)){
            return res;
        }
        String url= PropertyUtil.getPropValue("urlresource.emrurl");
        if(StringUtils.isEmpty(url)){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"urlresource.emrurl not set in config");
        }
        url+=patientId;
        res.put("showEmrButton",true);//是否显示
        res.put("emrUrl",url);//电子病历调用地址
        return res;
    }

    /**
     * 根据mpiid 获取中医馆病人id
     * @param mpiid
     * @return
     */
    @RpcService
    public String getPatientId(final String mpiid){
        HibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "select hosName,areaCode From Organ  where areaCode like :areaCode ";
                hql="select a.cardId  from HealthCard a ,Organ b where a.cardOrgan =b.organId and a.mpiId =:mpiId and b.source =:source";
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiid);
                q.setParameter("source", 1);
                //mark:默认取第一条，如果中医馆病人不同机构cardid不一致,后续可考虑前端医生选择指定机构，查看该患者的病历
                q.setMaxResults(1);
                Object result=q.uniqueResult();
                if(result!=null){
                    String cardId = (String) result;
                    setResult(cardId);
                }else {
                    setResult(null);
                }

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

}
