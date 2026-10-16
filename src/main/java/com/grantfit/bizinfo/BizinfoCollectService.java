package com.grantfit.bizinfo;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grantfit.program.SupportProgramMapper;

/**
 * 기업마당 공고를 받아 support_program 에 저장한다. (README §7, MILESTONE 1)
 */
@Service
public class BizinfoCollectService {

	private final BizinfoClient bizinfoClient;
	private final SupportProgramMapper supportProgramMapper;

	public BizinfoCollectService(BizinfoClient bizinfoClient, SupportProgramMapper supportProgramMapper) {
		this.bizinfoClient = bizinfoClient;
		this.supportProgramMapper = supportProgramMapper;
	}

	@Transactional
	public CollectResult collect(int pageIndex, int pageUnit) {
		List<BizinfoItem> items = bizinfoClient.fetch(pageIndex, pageUnit);

		int inserted = 0;
		for (BizinfoItem item : items) {
			if (supportProgramMapper.upsert(item.toSupportProgram())) {
				inserted++;
			}
		}
		return new CollectResult(items.size(), inserted, items.size() - inserted);
	}
}
