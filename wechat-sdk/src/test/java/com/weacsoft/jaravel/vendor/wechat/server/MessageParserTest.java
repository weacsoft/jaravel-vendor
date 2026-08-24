package com.weacsoft.jaravel.vendor.wechat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推送 XML → 类型化消息解析测试。
 */
class MessageParserTest {

    private static String xml(String body) {
        return "<xml>" + body + "</xml>";
    }

    @Test
    void testParseTextMessage() {
        String body =
                "<ToUserName><![CDATA[gh_oa]]></ToUserName>" +
                "<FromUserName><![CDATA[openid123]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[这是文本]]></Content>" +
                "<MsgId>1234567890</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertTrue(MessageParser.isText(msg));
        TextMessage text = MessageParser.asText(msg);
        assertEquals("这是文本", text.getContent());
        assertEquals("gh_oa", text.getToUserName());
        assertEquals("openid123", text.getFromUserName());
        assertEquals(1407564400L, text.getCreateTime());
        assertEquals(1234567890L, text.getMsgId());
    }

    @Test
    void testParseImageMessage() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[image]]></MsgType>" +
                "<PicUrl><![CDATA[http://mmbiz.qpic.cn/x]]></PicUrl>" +
                "<MediaId><![CDATA[media1]]></MediaId>" +
                "<MsgId>1</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertTrue(MessageParser.isImage(msg));
        ImageMessage image = (ImageMessage) msg;
        assertEquals("http://mmbiz.qpic.cn/x", image.getPicUrl());
        assertEquals("media1", image.getMediaId());
    }

    @Test
    void testParseSubscribeEvent() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[event]]></MsgType>" +
                "<Event><![CDATA[subscribe]]></Event>" +
                "<EventKey><![CDATA[qrscene_123]]></EventKey>" +
                "<Ticket><![CDATA[TICKET]]></Ticket>" +
                "<MsgId>2</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertTrue(MessageParser.isEvent(msg));
        EventMessage event = MessageParser.asEvent(msg);
        assertEquals(EventMessage.SUBSCRIBE, event.getEvent());
        assertTrue(event.isSubscribe());
        assertEquals("qrscene_123", event.getEventKey());
        assertEquals("TICKET", event.getTicket());
    }

    @Test
    void testParseLocationEvent() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[event]]></MsgType>" +
                "<Event><![CDATA[LOCATION]]></Event>" +
                "<Latitude>22.54</Latitude>" +
                "<Longitude>114.05</Longitude>" +
                "<Precision>119.01</Precision>" +
                "<Label><![CDATA[深圳]]></Label>" +
                "<MsgId>3</MsgId>";
        EventMessage event = (EventMessage) MessageParser.parse(xml(body));
        assertTrue(event.isLocation());
        assertEquals(22.54, event.getLatitude(), 0.001);
        assertEquals(114.05, event.getLongitude(), 0.001);
        assertEquals(119.01, event.getPrecision(), 0.01);
    }

    @Test
    void testParseScanEvent() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[event]]></MsgType>" +
                "<Event><![CDATA[SCAN]]></Event>" +
                "<EventKey><![CDATA[myScene]]></EventKey>" +
                "<Ticket><![CDATA[T456]]></Ticket>" +
                "<MsgId>4</MsgId>";
        EventMessage event = (EventMessage) MessageParser.parse(xml(body));
        assertTrue(event.isScan());
        assertEquals("myScene", event.getEventKey());
    }

    @Test
    void testParseSubscribeMsgSentEvent() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[event]]></MsgType>" +
                "<Event><![CDATA[subscribe_msg_sent_event]]></Event>" +
                "<WeAppId><![CDATA[wxmini]]></WeAppId>" +
                "<SubscribeMsgSentEvent>" +
                "<TemplateId>TPL1</TemplateId>" +
                "<List>" +
                "<item><TemplateId>TPL1</TemplateId><MsgID>M999</MsgID><ErrorCode>0</ErrorCode><ErrorStatus>ok</ErrorStatus></item>" +
                "<item><TemplateId>TPL2</TemplateId><MsgID>M998</MsgID><ErrorCode>43109</ErrorCode><ErrorStatus>user refuse</ErrorStatus></item>" +
                "</List>" +
                "</SubscribeMsgSentEvent>" +
                "<MsgId>5</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertTrue(msg instanceof SubscribeMsgSentEvent, "应解析为 SubscribeMsgSentEvent");
        SubscribeMsgSentEvent sent = (SubscribeMsgSentEvent) msg;
        assertEquals(2, sent.getItems().size());
        assertTrue(sent.getItems().get(0).isSuccess(), "ErrorCode=0 应视为成功");
        assertEquals("M999", sent.getItems().get(0).getMsgId());
        assertFalse(sent.getItems().get(1).isSuccess());
        assertEquals(43109, sent.getItems().get(1).getErrorCode());
    }

    @Test
    void testParseSubscribeMsgChangeEvent() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName>" +
                "<FromUserName><![CDATA[openid]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime>" +
                "<MsgType><![CDATA[event]]></MsgType>" +
                "<Event><![CDATA[subscribe_msg_change_event]]></Event>" +
                "<WeAppId><![CDATA[wxmini]]></WeAppId>" +
                "<SubscribeMsgChangeEvent>" +
                "<TemplateId>TPL1</TemplateId>" +
                "<List><item><TemplateId>TPL1</TemplateId><Status>1</Status><IsReject>false</IsReject></item></List>" +
                "</SubscribeMsgChangeEvent>" +
                "<MsgId>6</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertTrue(msg instanceof SubscribeMsgChangeEvent, "应解析为 SubscribeMsgChangeEvent");
        assertEquals(1, ((SubscribeMsgChangeEvent) msg).getItems().size());
    }

    @Test
    void testParseVoiceAndShortVideo() {
        String voice =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[voice]]></MsgType>" +
                "<MediaId><![CDATA[vm1]]></MediaId><Format><![CDATA[amr]]></Format>" +
                "<MsgId>7</MsgId>";
        VoiceMessage vm = (VoiceMessage) MessageParser.parse(xml(voice));
        assertEquals("vm1", vm.getMediaId());
        assertEquals("amr", vm.getFormat());

        String shortVideo =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[shortvideo]]></MsgType>" +
                "<MediaId><![CDATA[sv1]]></MediaId><ThumbMediaId><![CDATA[th1]]></ThumbMediaId>" +
                "<MsgId>8</MsgId>";
        ShortVideoMessage sv = (ShortVideoMessage) MessageParser.parse(xml(shortVideo));
        assertEquals("sv1", sv.getMediaId());
        assertEquals("th1", sv.getThumbMediaId());
    }

    @Test
    void testParseLocationMessage() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[location]]></MsgType>" +
                "<Location_X>23.13</Location_X><Location_Y>113.26</Location_Y>" +
                "<Scale>15</Scale><Label><![CDATA[广州]]></Label>" +
                "<MsgId>9</MsgId>";
        LocationMessage loc = (LocationMessage) MessageParser.parse(xml(body));
        assertEquals(23.13, loc.getLocationX(), 0.001);
        assertEquals(113.26, loc.getLocationY(), 0.001);
        assertEquals(15, loc.getScale());
        assertEquals("广州", loc.getLabel());
    }

    @Test
    void testParseLinkMessage() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[link]]></MsgType>" +
                "<Title><![CDATA[链接标题]]></Title><Description><![CDATA[描述]]></Description>" +
                "<Url><![CDATA[https://example.com/a]]></Url>" +
                "<MsgId>10</MsgId>";
        LinkMessage link = (LinkMessage) MessageParser.parse(xml(body));
        assertEquals("https://example.com/a", link.getUrl());
        assertEquals("链接标题", link.getTitle());
    }

    @Test
    void testUnknownMsgTypeThrows() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[unknown_type]]></MsgType>" +
                "<MsgId>11</MsgId>";
        UnsupportedMessageException ex = assertThrows(UnsupportedMessageException.class,
                () -> MessageParser.parse(xml(body)), "未知 MsgType 应抛 UnsupportedMessageException");
        assertTrue(ex.getMessage().toLowerCase().contains("unknown"), "异常应包含未知类型信息");
    }

    @Test
    void testAsTextRejectsWrongType() {
        String body =
                "<ToUserName><![CDATA[gh]]></ToUserName><FromUserName><![CDATA[open]]></FromUserName>" +
                "<CreateTime>1407564400</CreateTime><MsgType><![CDATA[image]]></MsgType>" +
                "<PicUrl><![CDATA[p]]></PicUrl><MediaId><![CDATA[m]]></MediaId><MsgId>12</MsgId>";
        ServerMessage msg = MessageParser.parse(xml(body));
        assertFalse(MessageParser.isText(msg));
        assertThrows(ClassCastException.class, () -> MessageParser.asText(msg),
                "非 text 消息 asText 应抛 ClassCastException");
    }

    @Test
    void testParseMalformedXmlThrows() {
        assertThrows(IllegalArgumentException.class, () -> MessageParser.parse("<xml><unclosed></xml>"),
                "非法 XML 应抛 IllegalArgumentException");
    }
}
