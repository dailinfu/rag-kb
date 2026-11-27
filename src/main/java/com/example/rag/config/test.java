package com.example.rag.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class test {
    public static void main(String[] args) {
        // 有序数组，找到合并后的中位数
        int[] nums1 = {1, 2, 3, 4, 5};
        int[] nums2 = {6, 7, 8, 9, 10};

        float result = test(nums1,nums2);
        System.out.println(result);
    }

    public static float test(int[] nums1,int[] nums2) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < nums1.length; i++) {
            list.add(nums1[i]);
        }
        for (int i = 0; i < nums2.length; i++) {
            list.add(nums2[i]);
        }
        Collections.sort(list);
        if (list.size()%2!= 0) {
            return list.get(list.size()/2);
        }else {
            int index = list.size() / 2;
            return (float) ((list.get(index)+list.get(index-1))/2.0);
        }
    }
}
