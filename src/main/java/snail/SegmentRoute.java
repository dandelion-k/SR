/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package snail;

import javafx.util.Pair;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MplsLabel;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Link;
import org.onosproject.net.Device;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import jxl.*;

/**
 * SegmentRoute by snail.
 */
@Component(immediate = true)
@Service
public class SegmentRoute implements SRService{

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;

    private ApplicationId appId;
    private ApplicationId defaultAppId;

    @Activate
    protected void activate() {
        //注册应用
        appId = coreService.registerApplication("SR");
        defaultAppId = coreService.getAppId((short)1);
//        flowRuleService.removeFlowRulesById(defaultAppId);
        print("SR Started!");

    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        print("SR Stopped!");
    }

    @Override
    public void SR(){
        //testLinks();
        //testInstallRule();
        //testInstallRule1();
        //testInstallRule2();
        //testIterable();
        //testHost();
        //testPair();
        //testLinks2Map();
    }

    private void installRules(){
        jxl.Workbook readwb = null;
        String inPath = "/home/snail/Applications/SR/segments3.xls";
        Iterable<Link> links = linkService.getLinks();
        Map<Pair<ConnectPoint,ConnectPoint>,long[]> linksMap = Links2Map(links);
        Map<Pair<Integer,Integer>,Integer> labelMap = new HashMap<Pair<Integer,Integer>,Integer>();
        try{
            InputStream instream = new FileInputStream(inPath);
            readwb = Workbook.getWorkbook(instream);

            // Sheet的下标是从0开始
            Sheet singelSegmentSheet = readwb.getSheet(0);
            labelMap = dealSingelSegment(singelSegmentSheet);

            Sheet pathSheet = readwb.getSheet(1);
            dealPath(pathSheet,labelMap,linksMap);
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            readwb.close();
        }
    }

    //给每个分段分配一个MPLS Label值
    private Map<Pair<Integer,Integer>,Integer> dealSingelSegment(Sheet sheet){
        // 获取Sheet表中所包含的总行数
        int rows = NumberOfRows(sheet);
        Map<Pair<Integer,Integer>,Integer> labelMap = new HashMap<Pair<Integer,Integer>,Integer>();
        for(int i=1;i<rows;i++){
            int label = i+1;
            Cell[] row = sheet.getRow(i);
            labelMap.put(new Pair<>(Integer.parseInt(row[0].getContents()),Integer.parseInt(row[1].getContents())),label);
        }
        return labelMap;
    }

    private void dealPath(Sheet sheet,Map<Pair<Integer,Integer>,Integer> labelMap,Map<Pair<ConnectPoint,ConnectPoint>,long[]> linksMap){
        int rows = NumberOfRows(sheet);
        for(int i=1;i<rows;i++){
            Cell[] row = sheet.getRow(i);

        }

        print("All flows added successfully!!");
    }

    private void installIngressRule(){
        //压入所有标签
        //转发
    }

    private void installMidRules(){
        //转发
    }

    private void installTailRules(){
        //弹出
        //转发
    }

    private void installEgressRules(){
        //弹出
    }

    private Map<Pair<ConnectPoint,ConnectPoint>,long[]> Links2Map(Iterable<Link> links){
        Map<Pair<ConnectPoint,ConnectPoint>,long[]> linksMap = new HashMap<Pair<ConnectPoint,ConnectPoint>,long[]>();
        for(Link link:links){
            linksMap.put(new Pair<>(link.src(),link.dst()), new long[]{link.src().port().toLong(),link.dst().port().toLong()});
        }
        return linksMap;
    }

    public void testLinks2Map(){
        Iterable<Link> links = linkService.getLinks();
        Map<Pair<ConnectPoint,ConnectPoint>,long[]> linksMap = Links2Map(links);
        for (Map.Entry<Pair<ConnectPoint,ConnectPoint>,long[]> entry : linksMap.entrySet()) {
            System.out.println("Key = " + entry.getKey().getKey().toString() + "," + entry.getKey().getValue().toString() + ", Value = " + String.valueOf(entry.getValue()[0])+","+String.valueOf(entry.getValue()[1]));
        }
    }

    public void testPair(){
        ConnectPoint a1 = ConnectPoint.deviceConnectPoint("of:0000000100000006/1");
        ConnectPoint a2 = ConnectPoint.deviceConnectPoint("of:0000000100000007/1");
        Pair<ConnectPoint,ConnectPoint> a = new Pair<>(a1,a2);
        print(a.getKey().toString());
        print(a.getValue().toString());
    }

    public void testHost(){
        Iterable<Host> hosts = hostService.getHosts();
        try{
            writeLoggerToFile("/home/snail/testLog/links", hosts.toString()+"\n");
        }catch(IOException e){
            print(e.toString());
        }
    }

    public void testInstallRule(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(IpPrefix.valueOf("10.0.3.0/24"))
                .matchIPDst(IpPrefix.valueOf("10.0.5.0/24"));
        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                .pushMpls()
                .setMpls(MplsLabel.mplsLabel(3))
                .pushMpls()
                .setMpls(MplsLabel.mplsLabel(4))
                .pushMpls()
                .setMpls(MplsLabel.mplsLabel(5))  //最后添加的是活动标签,一个Treatment添加标签不能超过3个
                .setOutput(PortNumber.portNumber("2"))
                .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment)
                .withPriority(10)  //数字越小优先级越高
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(100)
                .add();

        try {
            flowObjectiveService.forward(DeviceId.deviceId("of:0000000100000002"),forwardingObjective);
        }catch (Exception e){
            print(e.toString());
        }

    }

    public void testInstallRule1(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(5));
        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                .popMpls(Ethernet.MPLS_UNICAST)
                .popMpls(Ethernet.MPLS_UNICAST)
                .popMpls(Ethernet.TYPE_IPV4)    //弹出最后一个标签的时候类型应该是IPv4
                .setOutput(PortNumber.portNumber("4"))   //这里的出口不要按wireshark的写,要在onos中查看主机状态查看对应端口号
                .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment)
                .withPriority(10)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(100)
                .add();

        try {
            flowObjectiveService.forward(DeviceId.deviceId("of:0000000100000004"),forwardingObjective);
        }catch (Exception e){
            print(e.toString());
        }

    }

    public void testIterable(){
        Iterable<Link> links = linkService.getLinks();
        Iterator iterator = links.iterator();
        while (iterator.hasNext()){
            try{
                writeLoggerToFile("/home/snail/testLog/links", iterator.next().toString()+"\n");
            }catch(IOException e){
                print(e.toString());
            }
        }
    }

    public void testLinks(){
        Iterable<Link> links = linkService.getLinks();
        try{
            writeLoggerToFile("/home/snail/testLog/links", links.toString()+"\n");
        }catch(IOException e){
            print(e.toString());
        }
    }

    //将一串String写入文件
    private void writeLoggerToFile(String file, String conent) throws IOException {
        FileOutputStream FOS = new FileOutputStream(file, true);
        OutputStreamWriter OSW = new OutputStreamWriter(FOS);
        BufferedWriter out = new BufferedWriter(OSW);
        try {
            //out.write("\n");
            out.write(conent);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(out != null){
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int NumberOfRows(Sheet readsheet){
        int number=0;
        int rows=readsheet.getRows();
        for(int i=0;i<rows;i++){
            if(readsheet.getCell(0, i).getContents()!=""){
                number++;
            }else{
                break;
            }
        }
        return number;
    }

    private void print(String s){
        System.out.println(s);
    }

}
