package eh.cdr.dao;

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.Diagnosis;

import java.util.List;

public class DiagnosisDAO extends HibernateSupportDelegateDAO<Diagnosis>{
public DiagnosisDAO(){
	super();
	this.setEntityName(Diagnosis.class.getName());
	this.setKeyField("diagnosisID");
}

/**
 * 保存诊断记录
 */
@RpcService
public void saveDiagnosis(List<Diagnosis> dList){
	for(Diagnosis diagnosis :dList){
		save(diagnosis);
	}
}
}
