from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from nlu_extractor import WatsonxNluExtractor, ConversationParseRequest, ConversationParseResponse

app = FastAPI(
    title="Malrota AI Engine",
    description="말로타(Malrota) 고령자(디지털 소외계층) 및 교통약자용 NLU & 좌석 추천 AI 서버",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

extractor = WatsonxNluExtractor()

@app.get("/health")
def health_check():
    return {"status": "UP", "service": "malrota-ai-fastapi"}

@app.post("/api/conversation/parse", response_model=ConversationParseResponse)
def parse_conversation(request: ConversationParseRequest):
    return extractor.extract(request)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)