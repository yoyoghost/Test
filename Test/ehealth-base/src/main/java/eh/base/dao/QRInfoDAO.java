package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.base.constant.QRInfoConstant;
import eh.entity.base.QRInfo;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;

public abstract class QRInfoDAO extends HibernateSupportDelegateDAO<QRInfo> {
	public static final Logger log = Logger.getLogger(QRInfoDAO.class);

	public QRInfoDAO() {
		super();
		this.setEntityName(QRInfo.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 生成二维码的时候，插入数据
	 * @param info
	 * @return
     */
	public QRInfo saveQRInfo(QRInfo info){
		info.setCreateDate(new Date());
		info.setQrStatus(QRInfoConstant.QRSTATUS_EFFECTIVE);
		QRInfo savedInfo=save(info);
		return savedInfo;
	}

	@DAOMethod(sql = " from QRInfo where wxConfigId=:wxConfigId and qrInfo=:qrInfo and qrType=:qrType and qrStatus=1")
	public abstract List<QRInfo> findQRInfo(@DAOParam("wxConfigId") Integer wxConfigId, @DAOParam("qrInfo") String qrInfo, @DAOParam("qrType") Integer qrType);

	@DAOMethod(sql = "update QRInfo set qrStatus=0 where wxConfigId=:wxConfigId")
	public abstract void updateQRInfoByWxConfigId(@DAOParam("wxConfigId") Integer wxConfigId);

	@DAOMethod(sql = " from QRInfo where wxConfigId=:wxConfigId and qrType=3 and qrStatus=1")
	public abstract List<QRInfo> findMaterialQRInfo(@DAOParam("wxConfigId") Integer wxConfigId);


}
