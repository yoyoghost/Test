package eh.bus.dao;

import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.entity.bus.ConsultSet;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;

public abstract class ConsultSetWriteDAO extends HibernateSupportWriteDAO<ConsultSet>{
	
	public ConsultSetWriteDAO(){
		setEntityName(ConsultSet.class.getName());
		setKeyField("doctorId");
	}
	
	@Override
	protected void beforeSave(ConsultSet consultSet){

		//获取默认的业务设置价格
		Double onLineConsultPrice=new Double(ParamUtils.getParam(ParameterConstant.KEY_ONLINE_CONSULT_PRICE,"10"));
		Double appointConsultPrice=new Double(ParamUtils.getParam(ParameterConstant.KEY_APPOINT_CONSULT_PRICE,"20"));
		Double professorConsultPrice=new Double(ParamUtils.getParam(ParameterConstant.KEY_PROFESSOR_CONSULT_PRICE,"10"));
		Double recipeConsultPrice=new Double(ParamUtils.getParam(ParameterConstant.KEY_RECIPE_CONSULT_PRICE,"10"));

		consultSet.setOnLineStatus(consultSet.getOnLineStatus()==null?0:consultSet.getOnLineStatus());
		consultSet.setOnLineConsultPrice(consultSet.getOnLineConsultPrice()==null? onLineConsultPrice:consultSet.getOnLineConsultPrice());
		consultSet.setAppointStatus(consultSet.getAppointStatus()==null?0:consultSet.getAppointStatus());
		consultSet.setAppointConsultPrice(consultSet.getAppointConsultPrice()==null?appointConsultPrice:consultSet.getAppointConsultPrice());
		consultSet.setRemindInTen(consultSet.getRemindInTen()==null?true:consultSet.getRemindInTen());
		consultSet.setTransferStatus(consultSet.getTransferStatus()==null?0:consultSet.getTransferStatus());
		consultSet.setMeetClinicStatus(consultSet.getMeetClinicStatus()==null?0:consultSet.getMeetClinicStatus());
		consultSet.setPatientTransferStatus(consultSet.getPatientTransferStatus()==null?0:consultSet.getPatientTransferStatus());
		consultSet.setPatientTransferPrice(consultSet.getPatientTransferPrice()==null?0:consultSet.getPatientTransferPrice());
		consultSet.setSignStatus(consultSet.getSignStatus()==null?false:consultSet.getSignStatus());
		consultSet.setSignPrice(consultSet.getSignPrice()==null?0:consultSet.getSignPrice());
		consultSet.setProfessorConsultStatus(consultSet.getProfessorConsultStatus()==null?0:consultSet.getProfessorConsultStatus());
		consultSet.setProfessorConsultPrice(consultSet.getProfessorConsultPrice()==null?professorConsultPrice:consultSet.getProfessorConsultPrice());
		consultSet.setRecipeConsultStatus(consultSet.getRecipeConsultStatus()==null?0:consultSet.getRecipeConsultStatus());
		consultSet.setRecipeConsultPrice(consultSet.getRecipeConsultPrice()==null?recipeConsultPrice:consultSet.getRecipeConsultPrice());
	}


}