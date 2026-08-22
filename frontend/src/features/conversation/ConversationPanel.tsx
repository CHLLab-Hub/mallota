export function ConversationPanel() {
  return (
    <form className="conversation-panel" onSubmit={(event) => event.preventDefault()}>
      <label htmlFor="travel-request">어디로 가실 예정인가요?</label>
      <textarea
        id="travel-request"
        defaultValue="내일 오전 서울에서 대전 가는데 다리가 불편하고 창가가 좋아요."
        aria-describedby="setup-status"
      />
      <div className="panel-actions">
        <button className="primary-button" type="submit" disabled>
          음성 검색 준비 중
        </button>
        <p className="panel-status" id="setup-status" role="status">
          현재는 화면과 프로젝트 구조만 구성된 상태입니다.
        </p>
      </div>
    </form>
  )
}
