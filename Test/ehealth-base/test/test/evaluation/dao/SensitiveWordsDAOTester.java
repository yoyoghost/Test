package test.evaluation.dao;

import eh.entity.evaluation.SensitiveWords;
import eh.evaluation.dao.SensitiveWordsDAO;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by PC on 2016/11/2.
 */
public class SensitiveWordsDAOTester {
    private static ClassPathXmlApplicationContext appContext;
    private static SensitiveWordsDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("sensitiveWordsDAO", SensitiveWordsDAO.class);
    }

    @Test
    public void getBySensitiveWord() throws Exception {
        SensitiveWords sw = dao.findBySensitiveWords("sb").get(0);
        if (sw != null) {
            String word = sw.getSensitiveWord();
            System.out.println(word);
        }
    }

    @Test
    public void findAll() throws Exception {
        List<SensitiveWords> allWords = dao.findAll();
        if (allWords.size() > 0) {
            Set<String> set = null;
            set = new HashSet<String>();
            for (SensitiveWords fw : allWords) {
                fw.getSensitiveWord();
                set.add(fw.getSensitiveWord());
                System.out.println(set);
            }
        } else {//不存在抛出异常信息
            throw new Exception("敏感词库不存在");
        }
    }

    @Test
    public void findAllWords() throws Exception {
        List<String> allWords = dao.findAllWords();
        if (allWords.size() > 0) {
            for (String fw : allWords) {
                System.out.println(fw);
            }
        } else {//不存在抛出异常信息
            throw new Exception("敏感词库不存在");
        }
    }

    @Test
    public void updateRateByWords() throws Exception {
        dao.updateRateByWords(5, "A片");
        System.out.println("更新成功。。。。。。。。");
    }
}