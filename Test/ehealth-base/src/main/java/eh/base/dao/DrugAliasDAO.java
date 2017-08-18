package eh.base.dao;

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.base.DrugAlias;

public abstract class DrugAliasDAO extends
		HibernateSupportDelegateDAO<DrugAlias> implements
		DBDictionaryItemLoader<DrugAlias> {

	public DrugAliasDAO() {
		super();
		this.setEntityName(DrugAlias.class.getName());
		this.setKeyField("drugAliasId");
	}
}
