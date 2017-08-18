package eh.wxpay.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * User: rizenguo
 * Date: 2014/11/1
 * Time: 14:06
 */
public class XMLParser {

    /**
     * 此方法用于将格式化的xml数据文件转换为map
     *                                      but!!!! 此方法只能解析两层以内（包括根节点）的xml文件，且由于已有多处使用此方法，故不对此方法再做修改!!!
     *                                      but!!!! 此方法只能解析两层以内（包括根节点）的xml文件，且由于已有多处使用此方法，故不对此方法再做修改!!!
     *                                      but!!!! 此方法只能解析两层以内（包括根节点）的xml文件，且由于已有多处使用此方法，故不对此方法再做修改!!!
     * @param xmlString
     * @return
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    @Deprecated
    public static Map<String, Object> getMapFromXML(String xmlString) throws ParserConfigurationException, IOException, SAXException {

        if (xmlString.contains("encoding") == false) {//加header避免有中文时解析出错
            String encode = System.getProperty("file.encoding");
            String header = "<?xml version=\"1.0\" encoding=\"" + encode + "\"?>";
            xmlString = header + xmlString;
        }
        //这里用Dom的方式解析回包的最主要目的是防止API新增回包字段
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream is = Util.getStringStream(xmlString);
        Document document = builder.parse(is);

        //获取到document里面的全部结点
        NodeList allNodes = document.getFirstChild().getChildNodes();
        Node node;
        Map<String, Object> map = new HashMap<String, Object>();
        int i = 0;
        while (i < allNodes.getLength()) {
            node = allNodes.item(i);
            if (node instanceof Element) {
                map.put(node.getNodeName(), node.getTextContent());
            }
            i++;
        }
        return map;

    }

    /**
     * 多级节点
     *
     * @param xmlString
     * @return
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public static Map<String, Object> getMapFromXMLForVideo(String xmlString) throws ParserConfigurationException, IOException, SAXException {

        if (xmlString.contains("encoding") == false) {//加header避免有中文时解析出错
            String encode = System.getProperty("file.encoding");
            String header = "<?xml version=\"1.0\" encoding=\"" + encode + "\"?>";
            xmlString = header + xmlString;
        }
        //这里用Dom的方式解析回包的最主要目的是防止API新增回包字段
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream is = Util.getStringStream(xmlString);
        Document document = builder.parse(is);

        //获取到document里面的全部结点
        Map<String, Object> map = new HashMap<String, Object>();
        parseNode(map, document.getFirstChild());
        return map;

    }

    private static void parseNode(Map<String, Object> map, Node node) throws ParserConfigurationException, IOException, SAXException {
        if (node instanceof Element) {
            if (node.hasChildNodes()) {
                NodeList nodeList = node.getChildNodes();
                if (nodeList.getLength() > 1) {
                    Map<String, Object> subMap = new HashMap<String, Object>();
                    for (int i = 0; i < nodeList.getLength(); i++) {
                        parseNode(subMap, nodeList.item(i));
                    }
                    map.put(node.getNodeName(), subMap);
                } else {
                    map.put(node.getNodeName(), node.getTextContent());
                }
            }
        }
    }
}
