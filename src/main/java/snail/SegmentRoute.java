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
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MplsLabel;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
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
        //testInstallRule4();
        //testIterable();
        //testHost();
        //testPair();
        //testLinks2Map();
        installRules("/home/snail/Applications/SR/segments1.xls");
    }

    private void installRules(String inPath){
        jxl.Workbook readwb = null;
        Map<Pair<Integer,Integer>,Integer> labelMap = new HashMap<Pair<Integer,Integer>,Integer>();
        try{
            InputStream instream = new FileInputStream(inPath);
            readwb = Workbook.getWorkbook(instream);

            // Sheet的下标是从0开始
            Sheet singelSegmentSheet = readwb.getSheet(0);
            labelMap = dealSingelSegment(singelSegmentSheet);

            Sheet pathSheet = readwb.getSheet(1);
            dealPath(pathSheet,labelMap);
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
            int label = i;
            Cell[] row = sheet.getRow(i);
            labelMap.put(new Pair<>(Integer.parseInt(row[0].getContents()),Integer.parseInt(row[1].getContents())),label);
        }
        return labelMap;
    }

    private void dealPath(Sheet sheet,Map<Pair<Integer,Integer>,Integer> labelMap){
        int rows = NumberOfRows(sheet);
        for(int i=1;i<rows;i++){
            Cell[] row = sheet.getRow(i);

            //获取吓一跳出端口ID
            Map<Pair<DeviceId,DeviceId>,long[]> linksMap = linksMap();

            //获取路由器到主机的出口ID
            Map<Ip4Address,PortNumber> hostsMap = hostsMap();

            //获取过渡路由器编号,包括首位路由器
            int[] transitionRoutes = getTstRoutes(row[10],row[2],row[NumberOfColumns(row)-1]);

            //获取源目的IP
            IpPrefix[] IPs = new IpPrefix[]{ipPrefix(row[0].getContents()),ipPrefix(row[1].getContents())};
            int[] labels = getLabels(IPs,labelMap,transitionRoutes);

            //获取路径上所有路由器编号
            int[] path = getPath(row);

            //将路径分解为单个分段,如果过渡路由器的值为-1代表该路径只有一个分段
            boolean isSingel = transitionRoutes.length == 2;
            List<List<Integer>> segments = path2segments(path,transitionRoutes);

            //处理分段
            dealSegments(segments,isSingel,IPs,labels,linksMap,labelMap,transitionRoutes,hostsMap);
        }
        print("All flows added successfully!!");
    }

    private void installIngressRule(IpPrefix[] IPs,int[] labels,int deviceID,long portNum){
        //匹配,压入所有标签并转发
        if(labels.length < 4){
            TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
            selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPSrc(IPs[0])
                    .matchIPDst(IPs[1]);
            TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
            for (int i = 0; i < labels.length; i++) {
                trafficTreatment.pushMpls().setMpls(MplsLabel.mplsLabel(labels[labels.length - 1 - i]));
            }
            trafficTreatment.setOutput(PortNumber.portNumber(portNum));

            FlowRule flowRule = DefaultFlowRule.builder()
                    .withSelector(selectorBuilder.build())
                    .withTreatment(trafficTreatment.build())
                    .forTable(0)
                    .fromApp(appId)
                    .forDevice(deviceID(deviceID))
                    .makeTemporary(1000)
                    .withPriority(11)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
        }else{

        }
    }

    private void installMidRules(int label,int deviceID,long portNum){
        //匹配,转发
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(label));
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        trafficTreatment.setOutput(PortNumber.portNumber(portNum));

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment.build())
                .forTable(0)
                .fromApp(appId)
                .forDevice(deviceID(deviceID))
                .makeTemporary(1000)
                .withPriority(11)
                .build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private void installTailRules(int label,int nextLabel,int deviceID,long portNum){
        //匹配,弹出
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(label));
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        trafficTreatment.popMpls(Ethernet.MPLS_UNICAST);
        trafficTreatment.transition(1);

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment.build())
                .forTable(0)
                .fromApp(appId)
                .forDevice(deviceID(deviceID))
                .makeTemporary(1000)
                .withPriority(11)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        //匹配,转发
        TrafficSelector.Builder selectorBuilder1 = DefaultTrafficSelector.builder();
        selectorBuilder1.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(nextLabel));
        TrafficTreatment.Builder trafficTreatment1 = DefaultTrafficTreatment.builder();
        trafficTreatment1.setOutput(PortNumber.portNumber(portNum));

        FlowRule flowRule1 = DefaultFlowRule.builder()
                .withSelector(selectorBuilder1.build())
                .withTreatment(trafficTreatment1.build())
                .forTable(1)
                .fromApp(appId)
                .forDevice(deviceID(deviceID))
                .makeTemporary(1000)
                .withPriority(11)
                .build();
        flowRuleService.applyFlowRules(flowRule1);
    }

    private void installEgressRules(int deviceID,int label,Map<Ip4Address,PortNumber> hostsMap,IpPrefix[] IPs){
        //匹配,弹出
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(label));
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        trafficTreatment.popMpls(Ethernet.TYPE_IPV4);
        trafficTreatment.transition(1);

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment.build())
                .forTable(0)
                .fromApp(appId)
                .forDevice(deviceID(deviceID))
                .makeTemporary(1000)
                .withPriority(11)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        //匹配,转发
        TrafficSelector.Builder selectorBuilder1 = DefaultTrafficSelector.builder();
        selectorBuilder1.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(IPs[0])
                .matchIPDst(IPs[1]);
        TrafficTreatment.Builder trafficTreatment1 = DefaultTrafficTreatment.builder();
        String[] ips = IPs[1].address().getIp4Address().toString().split("\\.");
        Ip4Address ip = ip(ips[2]);
        PortNumber portNum = hostsMap.get(ip);
        trafficTreatment1.setOutput(portNum);

        FlowRule flowRule1 = DefaultFlowRule.builder()
                .withSelector(selectorBuilder1.build())
                .withTreatment(trafficTreatment1.build())
                .forTable(1)
                .fromApp(appId)
                .forDevice(deviceID(deviceID))
                .makeTemporary(1000)
                .withPriority(11)
                .build();
        flowRuleService.applyFlowRules(flowRule1);
    }

    private Map<Pair<DeviceId,DeviceId>,long[]> linksMap(){
        Iterable<Link> links = linkService.getLinks();
        Map<Pair<DeviceId,DeviceId>,long[]> linksMap = new HashMap<Pair<DeviceId,DeviceId>,long[]>();
        for(Link link:links){
            linksMap.put(new Pair<>(link.src().deviceId(),link.dst().deviceId()), new long[]{link.src().port().toLong(),link.dst().port().toLong()});
        }
        return linksMap;
    }

    private Map<Ip4Address,PortNumber> hostsMap(){
        Iterable<Host> hosts = hostService.getHosts();
        Map<Ip4Address,PortNumber> hostsMap = new HashMap<Ip4Address,PortNumber>();
        Iterator<Host> iterator = hosts.iterator();
        for(Host host:hosts){
            hostsMap.put(host.ipAddresses().iterator().next().getIp4Address(),host.location().port());
        }
        return hostsMap;
    }

    public void testLinks2Map(){
        Map<Pair<DeviceId,DeviceId>,long[]> linksMap = linksMap();
        for (Map.Entry<Pair<DeviceId,DeviceId>,long[]> entry : linksMap.entrySet()) {
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
        Iterator<Host> iterator = hosts.iterator();
        while (iterator.hasNext()) {
            try {
                writeLoggerToFile("/home/snail/testLog/links", iterator.next().ipAddresses().iterator().next().getIp4Address().toString() + "\n");
            } catch (IOException e) {
                print(e.toString());
            }
        }
    }

    public void testInstallRule(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(IpPrefix.valueOf("10.0.3.0/24"))
                .matchIPDst(IpPrefix.valueOf("10.0.5.0/24"));
        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
//                .pushMpls().setMpls(MplsLabel.mplsLabel(3))  //第一个添加的标签是栈底标签
//                .pushMpls().setMpls(MplsLabel.mplsLabel(4))
                .pushMpls().setMpls(MplsLabel.mplsLabel(5))  //最后添加的是活动标签,一个Treatment添加标签不能超过3个
                .setOutput(PortNumber.portNumber("2"))
//                .writeMetadata()
                .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment)
                .withPriority(10)  //数字越小优先级越高
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(1000)
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
//                .popMpls(Ethernet.MPLS_UNICAST)
//                .popMpls(Ethernet.MPLS_UNICAST)
                .popMpls(Ethernet.TYPE_IPV4)    //弹出最后一个标签的时候类型应该是IPv4
                .setOutput(PortNumber.portNumber("4"))   //这里的出口不要按wireshark的写,要在onos中查看主机状态查看对应端口号
                .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment)
                .withPriority(10)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(1000)
                .add();

        try {
            flowObjectiveService.forward(DeviceId.deviceId("of:0000000100000004"),forwardingObjective);
        }catch (Exception e){
            print(e.toString());
        }

    }

    public void testInstallRule4(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(IpPrefix.valueOf("10.0.3.0/24"))
                .matchIPDst(IpPrefix.valueOf("10.0.5.0/24"));
        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                .pushMpls().setMpls(MplsLabel.mplsLabel(3))  //第一个添加的标签是栈底标签
                .pushMpls().setMpls(MplsLabel.mplsLabel(4))
                .pushMpls().setMpls(MplsLabel.mplsLabel(5))  //最后添加的是活动标签,一个Treatment添加标签不能超过3
                .transition(1)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(trafficTreatment)
                .forTable(0)
                .fromApp(appId)
                .forDevice(DeviceId.deviceId("of:0000000100000002"))
                .makeTemporary(1000)
                .withPriority(10)
                .build();
        flowRuleService.applyFlowRules(flowRule);




        TrafficSelector.Builder selectorBuilder1 = DefaultTrafficSelector.builder();
        selectorBuilder1.matchEthType(Ethernet.MPLS_UNICAST)
                .matchMplsLabel(MplsLabel.mplsLabel(5));
        TrafficTreatment trafficTreatment1 = DefaultTrafficTreatment.builder()
                .pushMpls().setMpls(MplsLabel.mplsLabel(6))
//                .pushMpls().setMpls(MplsLabel.mplsLabel(7))
//                .pushMpls().setMpls(MplsLabel.mplsLabel(8))
                .setOutput(PortNumber.portNumber("2"))
                .build();

        FlowRule flowRule1 = DefaultFlowRule.builder()
                .withSelector(selectorBuilder1.build())
                .withTreatment(trafficTreatment1)
                .forTable(1)
                .fromApp(appId)
                .forDevice(DeviceId.deviceId("of:0000000100000002"))
                .makeTemporary(1000)
                .withPriority(10)
                .build();
        flowRuleService.applyFlowRules(flowRule1);

    }

    public void testIterable(){
        Iterable<Link> links = linkService.getLinks();
        Iterator<Link> iterator = links.iterator();
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

    private Ip4Address ip(String ip){
        Ip4Address ip4Address = Ip4Address.valueOf("10.0." + ip + ".1");
        return ip4Address;
    }

    private IpPrefix ipPrefix(String id){
        String IPStr = "10.0." + id + ".0/24";
        IpPrefix IP = IpPrefix.valueOf(IPStr);
        return IP;
    }

    private void dealSegments(List<List<Integer>> segments,
                              boolean isSingle,
                              IpPrefix[] IPs,
                              int[] labels,
                              Map<Pair<DeviceId,DeviceId>, long[]> linksMap,
                              Map<Pair<Integer,Integer>,Integer> labelMap,
                              int[] transitionRoutes,
                              Map<Ip4Address,PortNumber> hostsMap){
        int label;
        int nextLabel;
        if(isSingle){
            label = labelMap.get(new Pair<>(segments.get(0).get(0),segments.get(0).get(segments.get(0).size()-1)));
            dealSingleSegment(segments.get(0),IPs,labels,linksMap,label,hostsMap);
        }else{
            label = labelMap.get(new Pair<>(transitionRoutes[0],transitionRoutes[1]));
            nextLabel = labelMap.get(new Pair<>(transitionRoutes[1],transitionRoutes[2]));
            dealFirstSegment(segments.get(0),segments.get(1),IPs,labels,linksMap,label,nextLabel);
            for (int i = 1; i < segments.size() - 1; i++) {
                label = labelMap.get(new Pair<>(transitionRoutes[i],transitionRoutes[i+1]));
                nextLabel = labelMap.get(new Pair<>(transitionRoutes[i+1],transitionRoutes[i+2]));
                dealMidSegment(segments.get(i),segments.get(i+1),linksMap,label,nextLabel);
            }
            label = labelMap.get(new Pair<>(transitionRoutes[transitionRoutes.length-2],transitionRoutes[transitionRoutes.length-1]));
            dealLastSegment(segments.get(segments.size()-1),linksMap,label,hostsMap,IPs);
        }
    }

    private void dealFirstSegment(List<Integer> segment,List<Integer> nextSegment,IpPrefix[] IPs,int[] labels,Map<Pair<DeviceId,DeviceId>,long[]> linksMap,int label,int nextLabel){
        long portNum = linksMap.get(new Pair<>(deviceID(segment.get(0)),deviceID(segment.get(1))))[0];
        installIngressRule(IPs,labels,segment.get(0),portNum);
        for (int i = 1; i < segment.size(); i++) {
            if(i==segment.size()-1){
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(nextSegment.get(1))))[0];
                installTailRules(label,nextLabel,segment.get(i),portNum);
            }else{
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(segment.get(i+1))))[0];
                installMidRules(label,segment.get(i),portNum);
            }
        }
    }

    private void dealMidSegment(List<Integer> segment,List<Integer> nextSegment,Map<Pair<DeviceId,DeviceId>,long[]> linksMap,int label,int nextLabel){
        long portNum;
        for (int i = 1; i < segment.size(); i++) {
            if(i==segment.size()-1){
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(nextSegment.get(1))))[0];
                installTailRules(label,nextLabel,segment.get(i),portNum);
            }else{
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(segment.get(i+1))))[0];
                installMidRules(label,segment.get(i),portNum);
            }
        }
    }

    private void dealLastSegment(List<Integer> segment,Map<Pair<DeviceId,DeviceId>,long[]> linksMap,int label,Map<Ip4Address,PortNumber> hostsMap,IpPrefix[] IPs){
        long portNum;
        for (int i = 1; i < segment.size(); i++) {
            if(i==segment.size()-1){
                installEgressRules(segment.get(i),label,hostsMap,IPs);
            }else{
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(segment.get(i+1))))[0];
                installMidRules(label,segment.get(i),portNum);
            }
        }
    }

    private void dealSingleSegment(List<Integer> segment,IpPrefix[] IPs,int[] labels,Map<Pair<DeviceId,DeviceId>,long[]> linksMap,int label,Map<Ip4Address,PortNumber> hostsMap){
        long portNum = linksMap.get(new Pair<>(deviceID(segment.get(0)),deviceID(segment.get(1))))[0];
        installIngressRule(IPs,labels,segment.get(0),portNum);
        for (int i = 1; i < segment.size(); i++) {
            if(i==segment.size()-1){
                installEgressRules(segment.get(i),label,hostsMap,IPs);
            }else{
                portNum = linksMap.get(new Pair<>(deviceID(segment.get(i)),deviceID(segment.get(i+1))))[0];
                installMidRules(label,segment.get(i),portNum);
            }
        }
    }

    private int[] getPath(Cell[] row){
        int routesNum = NumberOfColumns(row) - 2;
        int[] path = new int[routesNum];
        for (int j = 0; j < routesNum; j++) {
            path[j] = Integer.parseInt(row[j+2].getContents());
        }
        return path;
    }

    private int[] getTstRoutes(Cell cell,Cell src,Cell dst){
        String[] transitionRoutesStr = cell.getContents().split(",");
        if(transitionRoutesStr.length == 1 && Integer.parseInt(transitionRoutesStr[0]) == -1){
            int[] transitionRoutes = new int[2];
            transitionRoutes[0] = Integer.parseInt(src.getContents());
            transitionRoutes[1] = Integer.parseInt(dst.getContents());
            return transitionRoutes;
        }
        int[] transitionRoutes = new int[transitionRoutesStr.length + 2];
        transitionRoutes[0] = Integer.parseInt(src.getContents());
        for (int j = 1; j < transitionRoutesStr.length + 1; j++) {
            transitionRoutes[j] = Integer.parseInt(transitionRoutesStr[j-1]);
        }
        transitionRoutes[transitionRoutesStr.length + 1] = Integer.parseInt(dst.getContents());
        return transitionRoutes;
    }

    private int[] getLabels(IpPrefix[] IPs, Map<Pair<Integer,Integer>,Integer> labelMap , int[] transitionRoutes){
        int[] labels = new int[transitionRoutes.length - 1];
        for (int i = 0; i < transitionRoutes.length - 1; i++) {
            labels[i] = labelMap.get(new Pair<>(transitionRoutes[i],transitionRoutes[i+1]));
        }
        return labels;
    }

    private List<List<Integer>> path2segments(int[] path,int[] transitionRoutes){
        List<List<Integer>> segments = new ArrayList<>();
        segments.add(new ArrayList<>());
        int nowSegment = 0;
        int nowTrasRouteIndex = 1;
        int nowTrasRoute;
        for (int i = 0; i < path.length; i++) {
            segments.get(nowSegment).add(path[i]);
            if(nowTrasRouteIndex >= transitionRoutes.length - 1){
                nowTrasRoute = -1;
            }else{
                nowTrasRoute = transitionRoutes[nowTrasRouteIndex];
            }
            if(path[i]== nowTrasRoute){
                segments.add(new ArrayList<>());
                nowSegment++;
                nowTrasRouteIndex++;
                segments.get(nowSegment).add(path[i]);
            }
        }
        return segments;
    }

    private DeviceId deviceID(int ID){
        DeviceId deviceId;
        if(ID<10){
            deviceId = DeviceId.deviceId("of:000000010000000"+String.valueOf(ID));
        }else if(ID<100){
            deviceId = DeviceId.deviceId("of:00000001000000"+String.valueOf(ID));
        }else{
            deviceId = DeviceId.deviceId("of:0000000100000"+String.valueOf(ID));
        }
        return deviceId;
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

    private int NumberOfColumns(Cell[] row){
        int number = 0;
        for(int i=0;i<row.length;i++){
            if(row[i].getContents()!=""){
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
