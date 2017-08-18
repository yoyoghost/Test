package eh.cdr.bean;

import java.util.List;

/**
 * 处方聊天记录消息
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/2/14.
 */
public class RecipeTagMsgBean implements java.io.Serializable {

    private static final long serialVersionUID = -3197298593269713489L;

    //处方序号
    private Integer recipeId;

    //会话Id
    private String sessionID;

    //诊断疾病名称
    private String diseaseName;

    //药品名称集合
    private List<String> drugNames;


    public RecipeTagMsgBean() {
    }

    public RecipeTagMsgBean(String diseaseName, List<String> drugNames, Integer recipeId, String sessionID) {
        this.diseaseName = diseaseName;
        this.drugNames = drugNames;
        this.recipeId = recipeId;
        this.sessionID = sessionID;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public List<String> getDrugNames() {
        return drugNames;
    }

    public void setDrugNames(List<String> drugNames) {
        this.drugNames = drugNames;
    }

    public Integer getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }
}
