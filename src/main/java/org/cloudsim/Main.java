package org.cloudsim;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

class CustomVM extends Vm {
    private double price;

    public CustomVM(int id, int userId, double mips, int numberOfPes, int ram, long bw, long size, String vmm, org.cloudbus.cloudsim.CloudletScheduler cloudletScheduler) {
        super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
        this.price = 0.0;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getPrice() {
        return this.price;
    }
}

public class Main {
    public static void main(String[] args) {
        try {
            int numUsers = 2;
            boolean traceFlag = false;
            CloudSim.init(numUsers, Calendar.getInstance(), traceFlag);

            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            DatacenterBroker brokerOptimal = createBroker();
            DatacenterBroker brokerNaive = createBroker();

            int brokerIdOptimal = brokerOptimal.getId();
            int brokerIdNaive = brokerNaive.getId();

            // For optimal strategy (cost-aware with cheaper VMs)
            List<CustomVM> vmListOptimal = createVMs(brokerIdOptimal, true);
            List<Cloudlet> optimalCloudlets = createCloudlets(brokerIdOptimal);

            Collections.sort(optimalCloudlets, Comparator.comparingLong(Cloudlet::getCloudletLength));
            Collections.sort(vmListOptimal, Comparator.comparingDouble(CustomVM::getPrice));
            assignOptimally(optimalCloudlets, vmListOptimal);

            brokerOptimal.submitVmList(new ArrayList<>(vmListOptimal));
            brokerOptimal.submitCloudletList(optimalCloudlets);

            // For naive strategy (more expensive VMs)
            List<CustomVM> vmListNaive = createVMs(brokerIdNaive, false);
            List<Cloudlet> naiveCloudlets = createCloudlets(brokerIdNaive);

            assignNaively(naiveCloudlets, vmListNaive);

            brokerNaive.submitVmList(new ArrayList<>(vmListNaive));
            brokerNaive.submitCloudletList(naiveCloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> listOptimal = brokerOptimal.getCloudletReceivedList();
            List<Cloudlet> listNaive = brokerNaive.getCloudletReceivedList();

            Log.printLine("\n\n=== Results for Optimal (Cost-Aware) Allocation ===");
            double costOptimal = printCloudletList(listOptimal, vmListOptimal);

            Log.printLine("\n\n=== Results for Naive (Random) Allocation ===");
            double costNaive = printCloudletList(listNaive, vmListNaive);

            // Summary
            Log.printLine("\n\n=== Summary ===");
            Log.printLine("Total Cost (Optimal): $" + new DecimalFormat("###.##").format(costOptimal));
            Log.printLine("Total Cost (Naive)  : $" + new DecimalFormat("###.##").format(costNaive));
            Log.printLine("Cost Saved by Optimal: $" + new DecimalFormat("###.##").format(costNaive - costOptimal));

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation error: " + e.getMessage());
        }
    }

    private static void assignOptimally(List<Cloudlet> cloudlets, List<CustomVM> vms) {
        for (Cloudlet cloudlet : cloudlets) {
            boolean assigned = false;
            for (CustomVM vm : vms) {
                if (cloudlet.getCloudletLength() <= vm.getMips() * 10) {
                    cloudlet.setVmId(vm.getId());
                    assigned = true;
                    break;
                }
            }
            if (!assigned) {
                cloudlet.setVmId(vms.get(vms.size() - 1).getId());
            }
        }
    }

    private static void assignNaively(List<Cloudlet> cloudlets, List<CustomVM> vms) {
        int vmIndex = 0;
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setVmId(vms.get(vmIndex).getId());
            vmIndex = (vmIndex + 1) % vms.size(); // Round-robin assignment
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(50000)));

        int hostId = 0;
        int ram = 32768;
        long storage = 1000000;
        int bw = 10000;

        hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    private static List<CustomVM> createVMs(int brokerId, boolean isCostAware) {
        List<CustomVM> vms = new ArrayList<>();

        int[] mips = {1000, 2000, 3000, 5000};
        double[] pricesCostAware = {0.05, 0.08, 0.12, 0.2}; // cheaper VMs
        double[] pricesNaive = {0.2, 0.3, 0.5, 0.6};        // expensive VMs

        int ram = 512;
        long bw = 1000;
        long size = 10000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < mips.length; i++) {
            CustomVM vm = new CustomVM(i, brokerId, mips[i], pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
            if (isCostAware) {
                vm.setPrice(pricesCostAware[i]);
            } else {
                vm.setPrice(pricesNaive[i]);
            }
            vms.add(vm);
        }

        return vms;
    }

    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> list = new ArrayList<>();

        long[] lengths = {1000, 2000, 5000, 8000, 10000, 15000, 20000, 25000, 30000, 40000};
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < lengths.length; i++) {
            Cloudlet cloudlet = new Cloudlet(i, lengths[i], pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            list.add(cloudlet);
        }

        return list;
    }

    private static double printCloudletList(List<Cloudlet> list, List<CustomVM> vms) {
        int size = list.size();
        Cloudlet cloudlet;
        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent + "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");
                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                        indent + indent + dft.format(cloudlet.getFinishTime()));
            }
        }

        double totalCost = 0.0;
        for (CustomVM vm : vms) {
            boolean used = false;
            for (Cloudlet i : list) {
                if (i.getVmId() == vm.getId()) {
                    used = true;
                    break;
                }
            }
            if (used) {
                totalCost += vm.getPrice();
            }
        }

        Log.printLine("Total cost: $" + dft.format(totalCost));
        return totalCost;
    }
}
