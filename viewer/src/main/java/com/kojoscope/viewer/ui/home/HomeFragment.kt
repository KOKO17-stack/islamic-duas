package com.kojoscope.viewer.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kojoscope.viewer.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fm = childFragmentManager
        if (fm.findFragmentById(R.id.liveContainer) == null) {
            val repo = com.kojoscope.viewer.net.DeviceRepo(requireContext())
            val id = repo.getSelectedDeviceId()
            fm.beginTransaction()
                .replace(R.id.liveContainer, LiveFragment.newInstance(id))
                .commit()
        }
    }
}

