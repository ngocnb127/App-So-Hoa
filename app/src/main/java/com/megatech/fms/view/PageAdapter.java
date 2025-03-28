package com.megatech.fms.view;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.R;
import com.megatech.fms.RefuelItemDetailFragment;
import com.megatech.fms.RefuelListFragment;

import java.nio.charset.Charset;
import java.util.List;

public class PageAdapter extends FragmentPagerAdapter {

    public PageAdapter(FragmentManager fm) {
        super(fm);
        fragmentManager = fm;
    }

    FragmentManager fragmentManager;

    @Override
    public Fragment getItem(int position) {
        List<Fragment> list = fragmentManager.getFragments();
        if (list.size() > 0) {
            RefuelListFragment fm = (RefuelListFragment) list.get(position);
            if (fm != null)
                return fm;
        }
        return new RefuelListFragment(position == 0);

    }

    @Override
    public int getCount() {
        return 2;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        if (position ==0)
            return FMSApplication.getApplication().getTruckNo();
        else
            return  FMSApplication.getApplication().getResources().getString(R.string.others);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return super.getItemPosition(object);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();

//        RefuelRecyclerView rv = (RefuelRecyclerView)item.getView();
//        rv.getAdapter().notifyDataSetChanged();

    }

    public void filter(CharSequence sequence) {
        int count = this.getCount();

        for (int i = 0; i < count; i++) {
            RefuelListFragment fragment = (RefuelListFragment) this.getItem(i);
            if (fragment != null)
                fragment.filter(sequence);
        }
    }
    public  void updateLists()
    {
        for (int i = 0; i < this.getCount(); i++) {
            RefuelListFragment fragment = (RefuelListFragment) this.getItem(i);
            if (fragment != null)
                fragment.onResume();
        }
    }
}
