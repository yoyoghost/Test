package eh.task.executor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import eh.cdr.dao.ClinicEmrDAO;
import eh.cdr.dao.ClinicEmrDetailDAO;
import eh.cdr.dao.DocIndexDAO;
import eh.entity.cdr.ClinicEmr;
import eh.entity.cdr.ClinicEmrDetail;
import eh.entity.cdr.DocIndex;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 保存医院门诊病历到平台[门诊病历获取服务]
 *
 * @author Qichengjian
 */
public class SaveHisClinicEmrExecutor implements ActionExecutor {
    private static final Log logger = LogFactory.getLog(SaveHisClinicEmrExecutor.class);
    /**
     * 线程池
     */
    private static ExecutorService executors = ExecutorRegister.register(Executors.newScheduledThreadPool(5));

    /**
     * 业务参数
     */
    private ClinicEmr clinicEmr;

    private List<ClinicEmrDetail> clinicEmrDetails;

    public SaveHisClinicEmrExecutor(ClinicEmr clinicEmr, List<ClinicEmrDetail> clinicEmrDetails) {
        this.clinicEmr = clinicEmr;
        this.clinicEmrDetails = clinicEmrDetails;
    }

    @Override
    public void execute() throws DAOException {
        executors.execute(new Runnable() {
            @Override
            public void run() {
                saveClinicEmrs();
            }
        });
    }

    private void saveClinicEmrs() throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            public void execute(StatelessSession ss) throws Exception {
                ClinicEmrDAO clinicEmrDao = DAOFactory.getDAO(ClinicEmrDAO.class);
                //保存医院门诊病历时检查是否已经存在该门诊病历
                if (clinicEmrDao.mpiExistClinicEmrByMpiAndClinicOrgan(clinicEmr)) {//已经存在患者病历
                    return;
                }

                ClinicEmr emr = clinicEmrDao.save(clinicEmr);
                //保存检验报告明细
                ClinicEmrDetailDAO emrDetailDao = DAOFactory.getDAO(ClinicEmrDetailDAO.class);
                for (ClinicEmrDetail clinicEmrDetail : clinicEmrDetails) {
                    clinicEmrDetail.setClinicEmrId(emr.getClinicEmrId());
                    logger.info("保存医院门诊病历明细:" + JSONUtils.toString(clinicEmrDetail));
                    emrDetailDao.saveClinicEmrDetail(clinicEmrDetail);
                }

                //保存患者病历文档索引
                DocIndex doc = new DocIndex();
                doc.setDocType("0");
                doc.setMpiid(clinicEmr.getMpiId());
                doc.setDocClass(0);
                doc.setDocTitle(clinicEmr.getChiefComplaint());//主诉
                doc.setCreateOrgan(clinicEmr.getClinicOrgan());
                doc.setCreateDepart(clinicEmr.getClinicDepart());
                doc.setDocSummary(clinicEmr.getChiefComplaint());
                doc.setCreateDoctor(clinicEmr.getCreateDoctor());
                doc.setCreateDate(new Date());
                doc.setGetDate(new Date());
                DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
                logger.info("保存医院门诊病历文档索引:" + JSONUtils.toString(doc));
                docIndexDAO.saveDocIndex(doc);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }
}
